package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.concurrency.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ListSyncRouterService {

    private static final Logger logger = LoggerFactory.getLogger(ListSyncRouterService.class);

    private final ListSyncStore listSyncStore;
    private final LockingListSyncStore lockingWrapper;

    @Autowired
    public ListSyncRouterService(ListSyncStore listSyncStore) {
        this.lockingWrapper = new LockingListSyncStore(Objects.requireNonNull(listSyncStore, "listSyncStore"));
        this.listSyncStore = this.lockingWrapper;
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
                || action == ActionType.DELETE || action == ActionType.CHECK_OFF
                || action == ActionType.CLAIM_ITEM || action == ActionType.UNCLAIM_ITEM) {
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

        if (action == ActionType.BULK_DELETE) {
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        }

        return switch (action) {
            case ADD, UPDATE, DELETE, CHECK_OFF, CLAIM_ITEM, UNCLAIM_ITEM -> listSyncStore.apply(listId, payload);
            case TYPING, UNKNOWN -> payload;
            case BULK_DELETE -> payload;
        };
    }

    /**
     * Processes a batch of updates chronologically through the concurrency controller.
     */
    public List<ListUpdatePayload> routeBatch(String listId, List<ListUpdatePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return Collections.emptyList();
        }

        logger.info("Processing batch of {} updates", payloads.size());

        // Sort by client-side timestamp to ensure chronological processing
        List<ListUpdatePayload> sortedPayloads = payloads.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(p -> p.getTimestamp() != null ? p.getTimestamp() : 0L))
                .toList();

        List<ListUpdatePayload> results = new ArrayList<>();
        for (ListUpdatePayload p : sortedPayloads) {
            try {
                results.add(route(listId, p));
            } catch (Exception e) {
                logger.error("Failed to route payload {} for list {}: {}", p.getItemId(), listId, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Periodic task to evict idle locks and rooms to prevent memory leaks.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void performCleanup() {
        lockingWrapper.cleanup();
    }

    private static final class LockingListSyncStore implements ListSyncStore {

        private final ListSyncStore delegate;
        private final Map<String, RoomManager> rooms = new ConcurrentHashMap<>();

        private LockingListSyncStore(ListSyncStore delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public ListUpdatePayload apply(String listId, ListUpdatePayload payload) {
            RoomManager room = rooms.computeIfAbsent(listId, id -> new RoomManager());
            ListUpdatePayload processed = room.processUpdate(payload);
            
            // If the concurrency controller rejected the update, return immediately without persisting
            if (ListUpdatePayload.STATUS_REJECTION.equals(processed.getStatus())) {
                return processed;
            }
            
            // Otherwise, delegate to the persistence store
            return delegate.apply(listId, processed);
        }

        public void cleanup() {
            logger.debug("Starting concurrency lock cleanup for {} rooms", rooms.size());
            rooms.keySet().forEach(listId -> rooms.compute(listId, (id, rm) -> {
                if (rm == null) return null;
                rm.cleanupIdleLocks();
                return rm.getActiveLockCount() == 0 ? null : rm;
            }));
        }
    }
}

