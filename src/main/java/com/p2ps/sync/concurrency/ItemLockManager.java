package com.p2ps.sync.concurrency;

import com.p2ps.dto.ListUpdatePayload;

/**
 * Manages concurrency and state for a single item within a shopping list.
 * Implements optimistic concurrency control using timestamps.
 */
public class ItemLockManager {
    private static final long LOCK_WINDOW_MILLIS = 50L;
    
    private long lockedUntilMillis;
    private long lastTimestamp;
    private long lastAccessedMillis;
    private Boolean lastChecked;
    private Long lastConfirmedTimestamp;

    /**
     * Attempts to acquire a lock and process the payload.
     * If the payload is stale (older timestamp), it marks it as REJECTION and returns current state.
     */
    public synchronized ListUpdatePayload process(ListUpdatePayload payload) {
        long currentTime = System.currentTimeMillis();
        
        // Wait if currently locked (primitive spin-wait/blocking)
        long waitMillis = lockedUntilMillis - currentTime;
        while (waitMillis > 0) {
            try {
                this.wait(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for item lock", e);
            }
            currentTime = System.currentTimeMillis();
            waitMillis = lockedUntilMillis - currentTime;
        }

        // Lock for processing window
        lockedUntilMillis = currentTime + LOCK_WINDOW_MILLIS;
        
        try {
            Long incomingTimestamp = payload.getTimestamp();
            
            // Check for stale updates
            if (incomingTimestamp != null && incomingTimestamp <= lastTimestamp) {
                payload.setChecked(this.lastChecked);
                payload.setTimestamp(this.lastConfirmedTimestamp);
                payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
                return payload;
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
            this.notifyAll();
        }
    }

    public synchronized boolean isIdle(long currentTime, long idleThresholdMillis) {
        return lockedUntilMillis <= currentTime && (currentTime - lastAccessedMillis > idleThresholdMillis);
    }
}
