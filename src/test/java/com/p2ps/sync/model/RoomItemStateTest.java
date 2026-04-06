package com.p2ps.sync.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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

        assertNotNull(readField(state, "id"));
        assertNotNull(readField(state, "lastUpdated"));
    }

    @Test
    void onUpdateRefreshesTimestamp() {
        RoomItemState state = new RoomItemState("list-1", "item-1");
        state.onCreate();

        setField(state, "lastUpdated", Instant.EPOCH);

        state.onUpdate();

        Instant updated = (Instant) readField(state, "lastUpdated");
        assertTrue(updated.isAfter(Instant.EPOCH));
    }

    @Test
    void constructorSetsListAndItemIds() {
        RoomItemState state = new RoomItemState("list-9", "item-9");

        assertEquals("list-9", readField(state, "listId"));
        assertEquals("item-9", readField(state, "itemId"));
    }

    @Test
    void noArgConstructorIsAvailableForJpa() {
        RoomItemState state = new RoomItemState();

        assertNull(readField(state, "id"));
        assertNull(readField(state, "listId"));
        assertNull(readField(state, "itemId"));
    }

    private static Object readField(RoomItemState state, String name) {
        try {
            Field field = RoomItemState.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(state);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void setField(RoomItemState state, String name, Object value) {
        try {
            Field field = RoomItemState.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(state, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
