package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.service.ItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class DatabaseListSyncStore implements ListSyncStore {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseListSyncStore.class);

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
        if (action != ActionType.CHECK_OFF && payload.getChecked() == null) {
            return payload;
        }

        boolean checked = payload.getChecked() != null ? payload.getChecked() : true;

        UUID uuid;
        try {
            uuid = UUID.fromString(itemId);
        } catch (IllegalArgumentException ex) {
            logger.warn("Ignoring sync update for non-UUID itemId={}, listId={}", itemId, listId);
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return payload;
        }

        try {
            ItemDTO updatedItem = itemService.updateItemStatus(uuid, checked, payload.getTimestamp());
            payload.setChecked(updatedItem.isChecked());
            payload.setTimestamp(updatedItem.getLastUpdatedTimestamp());
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        } catch (OptimisticLockingFailureException ex) {
            logger.warn("Optimistic locking rejected item sync update for listId={}, itemId={}", listId, itemId, ex);
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return payload;
        }
    }
}
