package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.model.RoomItemState;
import com.p2ps.sync.repository.RoomItemStateRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class DatabaseListSyncStore implements ListSyncStore {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseListSyncStore.class);

    private final RoomItemStateRepository roomItemStateRepository;

    public DatabaseListSyncStore(RoomItemStateRepository roomItemStateRepository) {
        this.roomItemStateRepository = roomItemStateRepository;
    }

    private void copyCanonicalFields(RoomItemState state, ListUpdatePayload payload) {
        payload.setContent(state.getContent());
        payload.setChecked(state.isChecked());
        payload.setTimestamp(state.getClientTimestamp());
    }

    private void clearCanonicalFieldsForDelete(RoomItemState state, ListUpdatePayload payload) {
        payload.setContent(null);
        payload.setChecked(null);
        payload.setTimestamp(state.getClientTimestamp());
    }

    private boolean handleTimestampRejectionIfOlder(RoomItemState state, ListUpdatePayload payload) {
        Long currentTimestamp = state.getClientTimestamp();
        Long incomingTimestamp = payload.getTimestamp();
        if (currentTimestamp != null && incomingTimestamp != null && incomingTimestamp < currentTimestamp) {
            copyCanonicalFields(state, payload);
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return true;
        }
        return false;
    }

    // Attempt to persist state; on optimistic lock failure set payload rejection and log
    private boolean trySave(RoomItemState state, ListUpdatePayload payload, boolean isDelete, String listId, String itemId, ActionType action) {
        try {
            roomItemStateRepository.saveAndFlush(state);
            return true;
        } catch (OptimisticLockingFailureException ex) {
            if (isDelete) {
                logger.warn("Optimistic locking rejected list sync delete for listId={}, itemId={}", listId, itemId, ex);
            } else {
                logger.warn("Optimistic locking rejected list sync update for listId={}, itemId={}, action={}",
                        listId, itemId, action, ex);
            }
            copyCanonicalFields(state, payload);
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return false;
        }
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
        RoomItemState state = roomItemStateRepository.findByListIdAndItemId(listId, itemId)
                .orElseGet(() -> new RoomItemState(listId, itemId));

        // Common timestamp rejection check
        if (handleTimestampRejectionIfOlder(state, payload)) {
            return payload;
        }

        if (action == ActionType.DELETE) {
            state.setDeleted(true);
            state.setDeletedAt(Instant.now());
            if (payload.getTimestamp() != null) {
                state.setClientTimestamp(payload.getTimestamp());
            }

            if (!trySave(state, payload, true, listId, itemId, action)) {
                return payload;
            }

            clearCanonicalFieldsForDelete(state, payload);
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        }

        if (payload.getContent() != null) {
            state.setContent(payload.getContent());
        }
        if (payload.getChecked() != null) {
            state.setChecked(Boolean.TRUE.equals(payload.getChecked()));
        } else if (action == ActionType.CHECK_OFF) {
            state.setChecked(!state.isChecked());
        }

        state.setDeleted(false);
        state.setDeletedAt(null);
        if (payload.getTimestamp() != null) {
            state.setClientTimestamp(payload.getTimestamp());
        }

        if (!trySave(state, payload, false, listId, itemId, action)) {
            return payload;
        }

        copyCanonicalFields(state, payload);
        payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
        return payload;
    }
}
