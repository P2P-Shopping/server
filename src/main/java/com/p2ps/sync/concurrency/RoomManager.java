package com.p2ps.sync.concurrency;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a set of ItemLockManagers for a specific shopping list (room).
 * Handles the lifecycle of item locks and provides cleanup logic.
 */
public class RoomManager {
    private static final long IDLE_THRESHOLD_MILLIS = 30000L; // 30 seconds
    
    private final Map<String, ItemLockManager> itemLocks = new ConcurrentHashMap<>();

    /**
     * Processes a single update payload through the appropriate item lock.
     */
    public ListUpdatePayload processUpdate(ListUpdatePayload payload) {
        String itemId = payload.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return payload;
        }

        ItemLockManager lock = itemLocks.computeIfAbsent(itemId, id -> new ItemLockManager());
        ListUpdatePayload result = lock.process(payload);

        // Immediate cleanup for deleted items
        if (payload.getAction() == ActionType.DELETE && ListUpdatePayload.STATUS_SUCCESS.equals(result.getStatus())) {
            itemLocks.remove(itemId);
        }

        return result;
    }

    /**
     * Evicts item locks that haven't been accessed recently to prevent memory leaks.
     */
    public void cleanupIdleLocks() {
        long now = System.currentTimeMillis();
        itemLocks.entrySet().removeIf(entry -> entry.getValue().isIdle(now, IDLE_THRESHOLD_MILLIS));
    }
    
    /**
     * Returns the number of active item locks (primarily for monitoring).
     */
    public int getActiveLockCount() {
        return itemLocks.size();
    }
}
