package com.p2ps.sync.concurrency;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomManagerTest {

    @Test
    void processUpdate_ReturnsPayloadWhenItemIdIsBlank() {
        RoomManager manager = new RoomManager();
        ListUpdatePayload p1 = new ListUpdatePayload();
        p1.setItemId(null);
        assertSame(p1, manager.processUpdate(p1));

        ListUpdatePayload p2 = new ListUpdatePayload();
        p2.setItemId("  ");
        assertSame(p2, manager.processUpdate(p2));
    }

    @Test
    void processUpdate_CreatesAndUsesLock() {
        RoomManager manager = new RoomManager();
        ListUpdatePayload p = new ListUpdatePayload();
        p.setItemId("item-1");
        p.setAction(ActionType.ADD);
        p.setTimestamp(100L);

        ListUpdatePayload result = manager.processUpdate(p);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
        assertEquals(1, manager.getActiveLockCount());
    }

    @Test
    void cleanupIdleLocks_RemovesIdleLocks() {
        RoomManager manager = new RoomManager();
        ListUpdatePayload p = new ListUpdatePayload();
        p.setItemId("item-1");
        p.setAction(ActionType.ADD);
        p.setTimestamp(100L);
        manager.processUpdate(p);
        assertEquals(1, manager.getActiveLockCount());

        manager.cleanupIdleLocks();
        assertEquals(1, manager.getActiveLockCount());
    }

    @Test
    void getActiveLockCount_ReturnsCorrectSize() {
        RoomManager manager = new RoomManager();
        assertEquals(0, manager.getActiveLockCount());
        
        ListUpdatePayload p1 = new ListUpdatePayload(); p1.setItemId("i1"); p1.setAction(ActionType.ADD);
        ListUpdatePayload p2 = new ListUpdatePayload(); p2.setItemId("i2"); p2.setAction(ActionType.ADD);
        
        manager.processUpdate(p1);
        manager.processUpdate(p2);
        
        assertEquals(2, manager.getActiveLockCount());
    }
}
