package com.p2ps.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.service.ItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class DatabaseListSyncStore implements ListSyncStore {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseListSyncStore.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ItemService itemService;

    public DatabaseListSyncStore(ItemService itemService) {
        this.itemService = itemService;
    }

    @Override
    public ListUpdatePayload apply(String listId, ListUpdatePayload payload) {
        if (listId == null || listId.isBlank() || payload == null) {
            return payload;
        }

        String itemId = payload.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return payload;
        }

        ActionType action = payload.getAction();
        if (action == null || action == ActionType.UNKNOWN) {
            return payload;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(itemId);
        } catch (IllegalArgumentException _) {
            logger.warn("Ignoring sync update for non-UUID itemId={}, listId={}", itemId, listId);
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return payload;
        }

        // For ADD and DELETE, the REST API has already performed the persistence.
        // We just need to mark as SUCCESS so the broker broadcasts it to other clients.
        if (action == ActionType.ADD || action == ActionType.DELETE) {
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        }

        // For CLAIM_ITEM and UNCLAIM_ITEM, persist the claim state via ItemService.
        if (action == ActionType.CLAIM_ITEM || action == ActionType.UNCLAIM_ITEM) {
            try {
                String claimedByEmail = action == ActionType.CLAIM_ITEM ? payload.getClaimedBy() : null;
                ItemDTO updatedItem = itemService.claimItem(uuid, claimedByEmail);
                payload.setClaimedBy(updatedItem.getClaimedBy());
                payload.setContent(objectMapper.writeValueAsString(updatedItem));
                payload.setTimestamp(updatedItem.getLastUpdatedTimestamp());
                payload.setChecked(updatedItem.isChecked());
                payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
                return payload;
            } catch (Exception ex) {
                logger.error("Claim update failed for listId={}, itemId={}, action={}",
                    listId, itemId, action, ex);
                payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
                return payload;
            }
        }

        // For CHECK_OFF and UPDATE, we perform a persistence check/update via ItemService.
        try {
            ItemDTO updatedItem = itemService.updateItemFromSync(uuid, payload);
            payload.setChecked(updatedItem.isChecked());
            payload.setTimestamp(updatedItem.getLastUpdatedTimestamp());
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        } catch (Exception ex) {
            logger.error("Sync update failed for listId={}, itemId={}, action={}", 
                listId, itemId, action, ex);
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return payload;
        }
    }
}
