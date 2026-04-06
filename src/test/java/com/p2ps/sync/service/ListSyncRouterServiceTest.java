package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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