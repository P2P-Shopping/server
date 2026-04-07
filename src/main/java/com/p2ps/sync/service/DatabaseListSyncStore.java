package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.model.RoomItemState;
import com.p2ps.sync.repository.RoomItemStateRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class DatabaseListSyncStore implements ListSyncStore {

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

        try {
            ActionType action = payload.getAction();
            RoomItemState state = roomItemStateRepository.findByListIdAndItemId(listId, itemId)
                    .orElseGet(() -> new RoomItemState(listId, itemId));

            if (action == ActionType.DELETE) {
                state.setDeleted(true);
                state.setDeletedAt(Instant.now());
                if (payload.getTimestamp() != null) {
                    state.setClientTimestamp(payload.getTimestamp());
                }
                roomItemStateRepository.save(state);
                payload.setTimestamp(state.getClientTimestamp());
                payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
                return payload;
            }

            Long currentTimestamp = state.getClientTimestamp();
            Long incomingTimestamp = payload.getTimestamp();
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

            roomItemStateRepository.save(state);

            payload.setContent(state.getContent());
            payload.setChecked(state.isChecked());
            payload.setTimestamp(state.getClientTimestamp());
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        } catch (OptimisticLockingFailureException ex) {
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return payload;
        }
    }
}
