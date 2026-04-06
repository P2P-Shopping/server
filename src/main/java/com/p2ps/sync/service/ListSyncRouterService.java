package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ListSyncRouterService {

    private static final Logger logger = LoggerFactory.getLogger(ListSyncRouterService.class);

    private final ListSyncStore listSyncStore;

    ListSyncRouterService() {
        this(new InMemoryListSyncStore());
    }

    @Autowired
    public ListSyncRouterService(ListSyncStore listSyncStore) {
        this.listSyncStore = listSyncStore;
    }

    /**
     * Routes list update payloads to the configured store.
     * Payload is required; listId is optional so blank destinations are ignored and the payload is returned unchanged.
     */
    public ListUpdatePayload route(String listId, ListUpdatePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null. Error thrown for: " + listId);
        }

        if (listId == null || listId.isBlank()) {
            logger.warn("Skipping sync routing because listId was blank; returning payload unchanged");
            return payload;
        }

        ActionType action = payload.getAction();
        logger.debug("Routing action {} for room {}", action, listId);

        // For persistent actions that operate on an item, if the itemId is blank
        // clear mutable fields so we don't accidentally propagate content/checked
        // values when the store cannot apply them.
        if (action == ActionType.ADD || action == ActionType.UPDATE
                || action == ActionType.DELETE || action == ActionType.CHECK_OFF) {
            String itemId = payload.getItemId();
            if (itemId == null || itemId.isBlank()) {
                logger.debug("Blank itemId for persistent action; clearing mutable fields");
                payload.setContent(null);
                payload.setChecked(null);
            }
        }

        return switch (action) {
            case ADD, UPDATE, DELETE, CHECK_OFF -> listSyncStore.apply(listId, payload);
            case TYPING, UNKNOWN -> payload;
        };
    }

    private static final class InMemoryListSyncStore implements ListSyncStore {

        private final Map<String, InMemoryItemState> states = new ConcurrentHashMap<>();

        @Override
        public ListUpdatePayload apply(String listId, ListUpdatePayload payload) {
            String itemId = payload.getItemId();
            if (itemId == null || itemId.isBlank()) {
                return payload;
            }

            String key = key(listId, itemId);
            ActionType action = payload.getAction();

            if (action == ActionType.DELETE) {
                states.remove(key);
                return payload;
            }

            InMemoryItemState state = states.computeIfAbsent(key, ignored -> new InMemoryItemState());

            synchronized (state) {
                if (payload.getContent() != null) {
                    state.content = payload.getContent();
                }
                if (payload.getChecked() != null) {
                    state.checked = payload.getChecked();
                } else if (action == ActionType.CHECK_OFF) {
                    state.checked = !state.checked;
                }

                payload.setContent(state.content);
                payload.setChecked(state.checked);
            }
            return payload;
        }

        private String key(String listId, String itemId) {
            return listId + "::" + itemId;
        }

        private static final class InMemoryItemState {
            private String content;
            private boolean checked;
        }
    }
}
