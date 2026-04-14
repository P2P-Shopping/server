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

    public ListSyncRouterService() {
        this(new LockingListSyncStore(new InMemoryListSyncStore()));
    }

    @Autowired
    public ListSyncRouterService(ListSyncStore listSyncStore) {
        this.listSyncStore = new LockingListSyncStore(listSyncStore);
    }

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
                long now = System.currentTimeMillis();

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

                state.lockedUntilMillis = now + LOCK_WINDOW_MILLIS;
                Long timestamp = payload.getTimestamp();
                if (timestamp != null && timestamp < state.lastTimestamp) {
                    payload.setChecked(state.checked);
                    payload.setTimestamp(state.timestamp);
                    payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
                    state.notifyAll();
                    state.lastAccessedMillis = now;
                    evictIfNeeded(key, state, false, previousLastAccessed, now);
                    return payload;
                }

                if (payload.getAction() == ActionType.CHECK_OFF && payload.getChecked() == null) {
                    payload.setChecked(state.checked == null ? Boolean.TRUE : !state.checked);
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
                state.lastAccessedMillis = now;
                boolean successfulDelete = payload.getAction() == ActionType.DELETE
                        && ListUpdatePayload.STATUS_SUCCESS.equals(routed.getStatus());
                evictIfNeeded(key, state, successfulDelete, previousLastAccessed, now);
                return routed;
            }
        }

        private void evictIfNeeded(String key, LockState state, boolean successfulDelete,
                                   long previousLastAccessed, long now) {
            if (!state.isLocked(now) && (successfulDelete
                    || (previousLastAccessed > 0 && now - previousLastAccessed > LOCK_WINDOW_MILLIS))) {
                locks.remove(key, state);
            }
        }

        private static final class LockState {
            private long lockedUntilMillis;
            private long lastTimestamp;
            private long lastAccessedMillis;
            private Boolean checked;
            private Long timestamp;

            private boolean isLocked(long now) {
                return lockedUntilMillis > now;
            }
        }
    }
}
