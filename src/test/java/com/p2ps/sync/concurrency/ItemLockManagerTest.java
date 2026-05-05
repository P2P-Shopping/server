package com.p2ps.sync.concurrency;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemLockManagerTest {

    @Test
    void process_SuccessOnNewUpdate() {
        ItemLockManager lock = new ItemLockManager();
        ListUpdatePayload p = new ListUpdatePayload();
        p.setAction(ActionType.ADD);
        p.setTimestamp(100L);
        p.setChecked(true);

        ListUpdatePayload result = lock.process(p);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
    }

    @Test
    void process_RejectsStaleUpdate() {
        ItemLockManager lock = new ItemLockManager();
        ListUpdatePayload p1 = new ListUpdatePayload();
        p1.setAction(ActionType.ADD);
        p1.setTimestamp(100L);
        p1.setChecked(true);
        lock.process(p1);

        ListUpdatePayload p2 = new ListUpdatePayload();
        p2.setAction(ActionType.UPDATE);
        p2.setTimestamp(50L);
        
        ListUpdatePayload result = lock.process(p2);
        assertEquals(ListUpdatePayload.STATUS_REJECTION, result.getStatus());
        assertEquals(true, result.getChecked()); // Should return last known state
        assertEquals(100L, result.getTimestamp());
    }

    @Test
    void process_HandlesDeletionAndTombstones() {
        ItemLockManager lock = new ItemLockManager();
        
        // Add
        ListUpdatePayload p1 = new ListUpdatePayload();
        p1.setAction(ActionType.ADD);
        p1.setTimestamp(100L);
        lock.process(p1);

        // Delete
        ListUpdatePayload p2 = new ListUpdatePayload();
        p2.setAction(ActionType.DELETE);
        p2.setTimestamp(110L);
        lock.process(p2);

        // Update on deleted item should be rejected
        ListUpdatePayload p3 = new ListUpdatePayload();
        p3.setAction(ActionType.UPDATE);
        p3.setTimestamp(120L);
        ListUpdatePayload r3 = lock.process(p3);
        assertEquals(ListUpdatePayload.STATUS_REJECTION, r3.getStatus());

        // Re-add should work
        ListUpdatePayload p4 = new ListUpdatePayload();
        p4.setAction(ActionType.ADD);
        p4.setTimestamp(130L);
        ListUpdatePayload r4 = lock.process(p4);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, r4.getStatus());
    }

    @Test
    void isIdle_ReturnsTrueAfterThreshold() throws InterruptedException {
        ItemLockManager lock = new ItemLockManager();
        lock.process(new ListUpdatePayload()); // Updates lastAccessedMillis
        
        long now = System.currentTimeMillis();
        assertFalse(lock.isIdle(now, 1000L));
        assertTrue(lock.isIdle(now + 2000L, 1000L));
    }

    @Test
    void process_HandlesNullTimestamp() {
        ItemLockManager lock = new ItemLockManager();
        ListUpdatePayload p = new ListUpdatePayload();
        p.setAction(ActionType.ADD);
        p.setTimestamp(null);
        
        ListUpdatePayload result = lock.process(p);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
    }
}
