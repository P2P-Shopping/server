package com.p2ps.sync.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoomItemStateTest {

    @Test
    void onCreateInitializesIdentityAndTimestamp() {
        RoomItemState state = new RoomItemState("list-1", "item-1");

        state.onCreate();

        assertNotNull(state.getId());
        assertNotNull(state.getLastUpdated());
    }

    @Test
    void onUpdateRefreshesTimestamp() {
        RoomItemState state = new RoomItemState("list-1", "item-1");
        state.onCreate();

        state.setLastUpdated(Instant.EPOCH);

        state.onUpdate();

        Instant updated = state.getLastUpdated();
        assertTrue(updated.isAfter(Instant.EPOCH));
    }

    @Test
    void constructorSetsListAndItemIds() {
        RoomItemState state = new RoomItemState("list-9", "item-9");

        assertEquals("list-9", state.getListId());
        assertEquals("item-9", state.getItemId());
    }

    @Test
    void noArgConstructorIsAvailableForJpa() {
        RoomItemState state = new RoomItemState();

        assertNull(state.getId());
        assertNull(state.getListId());
        assertNull(state.getItemId());
    }
}
