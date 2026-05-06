package com.p2ps.sync.concurrency;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;

/**
 * Manages concurrency and state for a single item within a shopping list.
 * Uses pessimistic/monitor-based locking with a synchronized process(...) method.
 * Implements staleness gating using timestamps to ensure chronological consistency.
 */
public class ItemLockManager {
    
    private long lastTimestamp = Long.MIN_VALUE;
    private long lastAccessedMillis;
    private Boolean lastChecked;
    private Long lastConfirmedTimestamp;
    private boolean deleted = false;

    /**
     * Attempts to process the payload under the item's monitor.
     * If the payload is stale or the item is deleted, it marks it as REJECTION.
     */
    public synchronized ListUpdatePayload process(ListUpdatePayload payload) {
        try {
            // Reject updates for items marked as deleted (tombstone)
            if (deleted && payload.getAction() != ActionType.ADD) {
                payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
                return payload;
            }

            Long incomingTimestamp = payload.getTimestamp();
            
            // Check for stale updates
            if (incomingTimestamp != null && incomingTimestamp <= lastTimestamp) {
                payload.setChecked(this.lastChecked);
                payload.setTimestamp(this.lastConfirmedTimestamp);
                payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
                return payload;
            }

            // Handle deletion
            if (payload.getAction() == ActionType.DELETE) {
                this.deleted = true;
            } else if (payload.getAction() == ActionType.ADD) {
                this.deleted = false;
            }

            // Update internal state for successful application
            if (payload.getChecked() != null) {
                this.lastChecked = payload.getChecked();
            }
            if (incomingTimestamp != null) {
                this.lastConfirmedTimestamp = incomingTimestamp;
                this.lastTimestamp = incomingTimestamp;
            }
            
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        } finally {
            this.lastAccessedMillis = System.currentTimeMillis();
        }
    }

    public synchronized boolean isIdle(long currentTime, long idleThresholdMillis) {
        // We don't remove tombstones immediately if they were recently accessed
        return currentTime - lastAccessedMillis > idleThresholdMillis;
    }
}

