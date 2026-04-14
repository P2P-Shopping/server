package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
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
        this.listSyncStore = new LockingListSyncStore(Objects.requireNonNull(listSyncStore, "listSyncStore"));
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

        if (action == ActionType.ADD || action == ActionType.UPDATE
                || action == ActionType.DELETE || action == ActionType.CHECK_OFF) {
            String itemId = payload.getItemId();
            if (itemId == null || itemId.isBlank()) {
                logger.debug("Blank itemId for persistent action; clearing mutable fields");
                payload.setContent(null);
                payload.setChecked(null);
            }
        }

        if (action == ActionType.CHECK_OFF && payload.getChecked() == null) {
            logger.debug("Rejecting CHECK_OFF without explicit checked value");
            payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
            return payload;
        }

        return switch (action) {
            case ADD, UPDATE, DELETE, CHECK_OFF -> listSyncStore.apply(listId, payload);
            case TYPING, UNKNOWN -> payload;
        };
    }

    private static final class InMemoryListSyncStore implements ListSyncStore {
        @Override
        public ListUpdatePayload apply(String listId, ListUpdatePayload payload) {
            return payload;
        }

    }

    private static final class LockingListSyncStore implements ListSyncStore {

        private static final long LOCK_WINDOW_MILLIS = 50L;

        private final ListSyncStore delegate;
        private final Map<String, LockState> locks = new ConcurrentHashMap<>();

        private LockingListSyncStore(ListSyncStore delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public ListUpdatePayload apply(String listId, ListUpdatePayload payload) {
            String itemId = payload.getItemId();
            if (itemId == null || itemId.isBlank()) {
                return delegate.apply(listId, payload);
            }

            String key = listId + "::" + itemId;
            LockState state = locks.computeIfAbsent(key, ignored -> new LockState());
            synchronized (state) {
                long previousLastAccessed = state.lastAccessedMillis;

                long waitMillis = state.lockedUntilMillis - System.currentTimeMillis();
                while (waitMillis > 0) {
                    try {
                        state.wait(waitMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for item lock: " + key, e);
                    }
                    waitMillis = state.lockedUntilMillis - System.currentTimeMillis();
                }

                long currentTime = System.currentTimeMillis();
                state.lockedUntilMillis = currentTime + LOCK_WINDOW_MILLIS;
                Long timestamp = payload.getTimestamp();
                if (timestamp != null && timestamp <= state.lastTimestamp) {
                    payload.setChecked(state.checked);
                    payload.setTimestamp(state.timestamp);
                    payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
                    state.notifyAll();
                    state.lastAccessedMillis = currentTime;
                    evictIfNeeded(key, state, false, previousLastAccessed, currentTime);
                    return payload;
                }

                ListUpdatePayload routed = delegate.apply(listId, payload);
                if (!ListUpdatePayload.STATUS_REJECTION.equals(routed.getStatus())) {
                    if (routed.getChecked() != null) {
                        state.checked = routed.getChecked();
                    }
                    if (routed.getTimestamp() != null) {
                        state.timestamp = routed.getTimestamp();
                        state.lastTimestamp = routed.getTimestamp();
                    }
                }

                state.notifyAll();
                state.lastAccessedMillis = currentTime;
                boolean successfulDelete = payload.getAction() == ActionType.DELETE
                        && ListUpdatePayload.STATUS_SUCCESS.equals(routed.getStatus());
                evictIfNeeded(key, state, successfulDelete, previousLastAccessed, currentTime);
                return routed;
            }
        }

        private void evictIfNeeded(String key, LockState state, boolean successfulDelete,
                                   long previousLastAccessed, long currentTime) {
            if (successfulDelete || (!state.isLocked(currentTime)
                    && previousLastAccessed > 0
                    && currentTime - previousLastAccessed > LOCK_WINDOW_MILLIS)) {
                locks.remove(key, state);
            }
        }

        private static final class LockState {
            private long lockedUntilMillis;
            private long lastTimestamp;
            private long lastAccessedMillis;
            private Boolean checked;
            private Long timestamp;

            private boolean isLocked(long currentTime) {
                return lockedUntilMillis > currentTime;
            }
        }
    }
}
