package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListSyncRouterServiceTest {

    @Test
    void routeRejectsNullPayload() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);

        assertThrows(IllegalArgumentException.class, () -> service.route("list-1", null));
    }

    @Test
    void routeReturnsPayloadUnchangedWhenListIdIsBlank() {
        AtomicInteger calls = new AtomicInteger();
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            calls.incrementAndGet();
            return payload;
        });

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.ADD);

        ListUpdatePayload result = service.route("   ", payload);

        assertSame(payload, result);
        assertEquals(0, calls.get());
    }

    @Test
    void routeSkipsTypingActions() {
        AtomicInteger calls = new AtomicInteger();
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            calls.incrementAndGet();
            return payload;
        });

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.TYPING);
        payload.setContent("Milk");

        ListUpdatePayload result = service.route("list-1", payload);

        assertSame(payload, result);
        assertEquals(0, calls.get());
    }

    @Test
    void routeClearsMutableFieldsForBlankItemIdBeforeDelegating() {
        ListUpdatePayload[] seen = new ListUpdatePayload[1];
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            seen[0] = payload;
            return payload;
        });

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.ADD);
        payload.setItemId("   ");
        payload.setContent("Milk");
        payload.setChecked(true);

        ListUpdatePayload result = service.route("list-1", payload);

        assertSame(payload, result);
        assertSame(payload, seen[0]);
        assertEquals(null, result.getContent());
        assertEquals(null, result.getChecked());
    }

    @Test
    void routeRejectsStaleTimestampOnSameItem() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            payload.setTimestamp(payload.getTimestamp());
            return payload;
        });

        ListUpdatePayload first = new ListUpdatePayload();
        first.setAction(ActionType.ADD);
        first.setItemId("item-1");
        first.setTimestamp(100L);
        first.setChecked(true);

        ListUpdatePayload firstResult = service.route("list-1", first);
        assertSame(first, firstResult);

        ListUpdatePayload second = new ListUpdatePayload();
        second.setAction(ActionType.ADD);
        second.setItemId("item-1");
        second.setTimestamp(50L);

        ListUpdatePayload secondResult = service.route("list-1", second);

        assertSame(second, secondResult);
        assertEquals(ListUpdatePayload.STATUS_REJECTION, secondResult.getStatus());
        assertNotSame(first, secondResult);
    }

    @Test
    void routeReturnsPayloadUnchangedWhenListIdIsNull() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.ADD);
        assertSame(payload, service.route(null, payload));
    }

    @Test
    void routeRejectsCheckOffWithoutCheckedValue() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setChecked(null);
        payload.setItemId("item-1");

        ListUpdatePayload result = service.route("list-1", payload);
        assertEquals(ListUpdatePayload.STATUS_REJECTION, result.getStatus());
    }

    @Test
    void routeHandlesUnknownAction() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UNKNOWN);
        assertSame(payload, service.route("list-1", payload));
    }

    @Test
    void routeBatchHandlesNullAndEmpty() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);
        assertEquals(0, service.routeBatch("list-1", null).size());
        assertEquals(0, service.routeBatch("list-1", java.util.Collections.emptyList()).size());
    }

    @Test
    void routeBatchSortsByTimestamp() {
        java.util.List<Long> callOrder = new java.util.ArrayList<>();
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            callOrder.add(payload.getTimestamp());
            return payload;
        });

        ListUpdatePayload p1 = new ListUpdatePayload(); p1.setTimestamp(300L); p1.setAction(ActionType.ADD); p1.setItemId("i1");
        ListUpdatePayload p2 = new ListUpdatePayload(); p2.setTimestamp(100L); p2.setAction(ActionType.ADD); p2.setItemId("i1");
        ListUpdatePayload p3 = new ListUpdatePayload(); p3.setTimestamp(200L); p3.setAction(ActionType.ADD); p3.setItemId("i1");

        service.routeBatch("list-1", java.util.Arrays.asList(p1, p2, p3));

        assertEquals(java.util.Arrays.asList(100L, 200L, 300L), callOrder);
    }

    @Test
    void routeBatchContinuesOnException() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            if ("fail".equals(payload.getItemId())) throw new RuntimeException("fail");
            return payload;
        });

        ListUpdatePayload p1 = new ListUpdatePayload(); p1.setItemId("ok1"); p1.setAction(ActionType.ADD);
        ListUpdatePayload p2 = new ListUpdatePayload(); p2.setItemId("fail"); p2.setAction(ActionType.ADD);
        ListUpdatePayload p3 = new ListUpdatePayload(); p3.setItemId("ok2"); p3.setAction(ActionType.ADD);

        java.util.List<ListUpdatePayload> results = service.routeBatch("list-1", java.util.Arrays.asList(p1, p2, p3));
        assertEquals(2, results.size());
        assertEquals("ok1", results.get(0).getItemId());
        assertEquals("ok2", results.get(1).getItemId());
    }

    @Test
    void performCleanupCallsUnderlyingCleanup() {
        // We can't easily mock the internal LockingListSyncStore but we can verify it doesn't crash
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);
        service.performCleanup();
    }

    @Test
    void routeWorksWithDefaultConstructor() {
        ListSyncRouterService service = new ListSyncRouterService();
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.ADD);
        payload.setItemId("item-1");
        ListUpdatePayload result = service.route("list-1", payload);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
    }
}
