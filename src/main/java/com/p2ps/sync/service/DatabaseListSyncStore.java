package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.model.RoomItemState;
import com.p2ps.sync.repository.RoomItemStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        ActionType action = payload.getAction();
        if (action == ActionType.DELETE) {
            roomItemStateRepository.deleteByListIdAndItemId(listId, itemId);
            return payload;
        }

        RoomItemState state = roomItemStateRepository.findByListIdAndItemId(listId, itemId)
                .orElseGet(() -> new RoomItemState(listId, itemId));
        state.setListId(listId);
        state.setItemId(itemId);

        if (payload.getContent() != null) {
            state.setContent(payload.getContent());
        }
        if (payload.getChecked() != null) {
            state.setChecked(Boolean.TRUE.equals(payload.getChecked()));
        } else if (action == ActionType.CHECK_OFF) {
            state.setChecked(!state.isChecked());
        }

        roomItemStateRepository.save(state);

        payload.setContent(state.getContent());
        payload.setChecked(state.isChecked());
        return payload;
    }
}