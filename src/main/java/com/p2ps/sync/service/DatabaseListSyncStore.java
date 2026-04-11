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

        Long currentTimestamp = state.getClientTimestamp();
        Long incomingTimestamp = payload.getTimestamp();

        if (action == ActionType.DELETE) {
            if (currentTimestamp != null && incomingTimestamp != null && incomingTimestamp < currentTimestamp) {
                payload.setContent(state.getContent());
                payload.setChecked(state.isChecked());
                payload.setTimestamp(currentTimestamp);
                payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
                return payload;
            }

            state.setDeleted(true);
            state.setDeletedAt(Instant.now());
            if (incomingTimestamp != null) {
                state.setClientTimestamp(incomingTimestamp);
            }
            try {
                roomItemStateRepository.saveAndFlush(state);
            } catch (OptimisticLockingFailureException ex) {
                logger.warn("Optimistic locking rejected list sync delete for listId={}, itemId={}", listId, itemId, ex);
                payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
                return payload;
            }
            payload.setTimestamp(state.getClientTimestamp());
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        }

        if (currentTimestamp != null && incomingTimestamp != null && incomingTimestamp < currentTimestamp) {
            payload.setContent(state.getContent());
            payload.setChecked(state.isChecked());
            payload.setTimestamp(currentTimestamp);
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
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
        if (incomingTimestamp != null) {
            state.setClientTimestamp(incomingTimestamp);
        }

        try {
            roomItemStateRepository.saveAndFlush(state);
        } catch (OptimisticLockingFailureException ex) {
            logger.warn("Optimistic locking rejected list sync update for listId={}, itemId={}, action={}",
                    listId, itemId, payload.getAction(), ex);
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return payload;
        }

        payload.setContent(state.getContent());
        payload.setChecked(state.isChecked());
        payload.setTimestamp(state.getClientTimestamp());
        payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
        return payload;
    }
}
