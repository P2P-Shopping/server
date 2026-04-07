package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.model.RoomItemState;
import com.p2ps.sync.repository.RoomItemStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class DatabaseListSyncStore implements ListSyncStore {

    private final RoomItemStateRepository roomItemStateRepository;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

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

        synchronized (locks.computeIfAbsent(key(listId, itemId), ignored -> new Object())) {
            ActionType action = payload.getAction();
            if (action == ActionType.DELETE) {
                // In-memory delete can race with concurrent computeIfAbsent/update calls; acceptable for dev/test use.
                roomItemStateRepository.deleteByListIdAndItemId(listId, itemId);
                payload.setStatus("Success");
                return payload;
            }

            RoomItemState state = roomItemStateRepository.findByListIdAndItemId(listId, itemId)
                    .orElseGet(() -> new RoomItemState(listId, itemId));

            Long currentTimestamp = state.getClientTimestamp();
            Long incomingTimestamp = payload.getTimestamp();
            if (currentTimestamp != null && incomingTimestamp != null && incomingTimestamp >= currentTimestamp) {
                payload.setContent(state.getContent());
                payload.setChecked(state.isChecked());
                payload.setTimestamp(currentTimestamp);
                payload.setStatus("Rejection");
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

            if (incomingTimestamp != null) {
                state.setClientTimestamp(incomingTimestamp);
            }

            roomItemStateRepository.save(state);

            payload.setContent(state.getContent());
            payload.setChecked(state.isChecked());
            payload.setTimestamp(state.getClientTimestamp());
            payload.setStatus("Success");
            return payload;
        }
    }

    private String key(String listId, String itemId) {
        return listId + "::" + itemId;
    }
}
