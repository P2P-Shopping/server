package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListSyncRouterServiceTest {

    @Test
    void routesPersistentActionsThroughTheStore() {
        RecordingStore store = new RecordingStore();
        ListSyncRouterService routerService = new ListSyncRouterService(store);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId("item-1");
        payload.setContent("Milk");
        payload.setChecked(Boolean.TRUE);

        ListUpdatePayload result = routerService.route("list-1", payload);

        assertSame(payload, result);
        assertEquals(1, store.invocationCount);
        assertEquals("list-1", store.lastListId);
        assertSame(payload, store.lastPayload);
    }

    @Test
    void routesTypingActionsStraightBackToClients() {
        RecordingStore store = new RecordingStore();
        ListSyncRouterService routerService = new ListSyncRouterService(store);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.TYPING);

        ListUpdatePayload result = routerService.route("list-1", payload);

        assertSame(payload, result);
        assertEquals(0, store.invocationCount);
    }

    @Test
    void leavesUnknownActionsUnchanged() {
        RecordingStore store = new RecordingStore();
        ListSyncRouterService routerService = new ListSyncRouterService(store);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UNKNOWN);
        payload.setItemId("item-1");

        ListUpdatePayload result = routerService.route("list-1", payload);

        assertSame(payload, result);
        assertEquals(0, store.invocationCount);
    }

    @Test
    void returnsPayloadWhenRoomIdIsBlank() {
        RecordingStore store = new RecordingStore();
        ListSyncRouterService routerService = new ListSyncRouterService(store);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);

        ListUpdatePayload result = routerService.route("   ", payload);

        assertSame(payload, result);
        assertEquals(0, store.invocationCount);
    }

    @Test
    void throwsWhenPayloadIsNull() {
        ListSyncRouterService routerService = new ListSyncRouterService(new RecordingStore());

        assertThrows(IllegalArgumentException.class, () -> routerService.route("list-1", null));
    }

    @Test
    void ignoresBlankItemIdsInTheStore() {
        RecordingStore store = new RecordingStore();
        ListSyncRouterService routerService = new ListSyncRouterService(store);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        payload.setItemId(" ");

        ListUpdatePayload result = routerService.route("list-1", payload);

        assertSame(payload, result);
        assertNull(payload.getContent());
        assertEquals(1, store.invocationCount);
    }

    @Test
    void routesPersistentActionsThroughTheDefaultStore() {
        ListSyncRouterService routerService = new ListSyncRouterService();

        ListUpdatePayload add = new ListUpdatePayload();
        add.setAction(ActionType.ADD);
        add.setItemId("item-1");
        add.setContent("Milk");
        add.setChecked(Boolean.FALSE);

        ListUpdatePayload added = routerService.route("list-1", add);
        assertSame(add, added);
        assertEquals("Milk", add.getContent());
        assertEquals(Boolean.FALSE, add.getChecked());

        ListUpdatePayload toggle = new ListUpdatePayload();
        toggle.setAction(ActionType.CHECK_OFF);
        toggle.setItemId("item-1");

        ListUpdatePayload toggled = routerService.route("list-1", toggle);
        assertSame(toggle, toggled);
        assertEquals("Milk", toggle.getContent());
        assertEquals(Boolean.TRUE, toggle.getChecked());

        ListUpdatePayload delete = new ListUpdatePayload();
        delete.setAction(ActionType.DELETE);
        delete.setItemId("item-1");

        ListUpdatePayload deleted = routerService.route("list-1", delete);
        assertSame(delete, deleted);

        ListUpdatePayload update = new ListUpdatePayload();
        update.setAction(ActionType.UPDATE);
        update.setItemId("item-1");
        update.setContent("Bread");

        ListUpdatePayload updated = routerService.route("list-1", update);
        assertSame(update, updated);
        assertEquals("Bread", update.getContent());
        assertEquals(Boolean.FALSE, update.getChecked());
    }

    @Test
    void ignoresBlankItemIdsInTheDefaultStore() {
        ListSyncRouterService routerService = new ListSyncRouterService();

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        payload.setItemId("   ");

        ListUpdatePayload result = routerService.route("list-1", payload);

        assertSame(payload, result);
        assertNull(payload.getContent());
        assertNull(payload.getChecked());
    }

    private static final class RecordingStore implements ListSyncStore {

        private int invocationCount;
        private String lastListId;
        private ListUpdatePayload lastPayload;

        @Override
        public ListUpdatePayload apply(String listId, ListUpdatePayload payload) {
            invocationCount++;
            lastListId = listId;
            lastPayload = payload;
            return payload;
        }
    }
}
