package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void routeReturnsPayloadUnchangedForUnknownActions() {
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
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);
        assertDoesNotThrow(service::performCleanup);
    }

    @Test
    void routeWorksWithStubStore() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.ADD);
        payload.setItemId("item-1");
        ListUpdatePayload result = service.route("list-1", payload);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
    }

    @Test
    void routeHandlesDeleteActionAndSupportsEviction() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        });

        ListUpdatePayload deletePayload = new ListUpdatePayload();
        deletePayload.setAction(ActionType.DELETE);
        deletePayload.setItemId("item-to-delete");

        ListUpdatePayload result = service.route("list-1", deletePayload);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
    }

    @Test
    void routeHandlesTypingAction() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.TYPING);
        payload.setContent("User is typing...");

        ListUpdatePayload result = service.route("list-1", payload);

        assertSame(payload, result);
        assertEquals("User is typing...", result.getContent());
    }

    @Test
    void routeWaitOnLock() throws Exception {
        java.util.concurrent.CountDownLatch firstEnter = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch secondWait = new java.util.concurrent.CountDownLatch(1);
        
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            firstEnter.countDown();
            try {
                secondWait.await();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            return payload;
        });

        ListUpdatePayload p1 = new ListUpdatePayload();
        p1.setAction(ActionType.UPDATE);
        p1.setItemId("item-1");

        ListUpdatePayload p2 = new ListUpdatePayload();
        p2.setAction(ActionType.UPDATE);
        p2.setItemId("item-1");

        Thread t1 = new Thread(() -> service.route("list-1", p1));
        t1.start();
        
        firstEnter.await();
        
        Thread t2 = new Thread(() -> service.route("list-1", p2));
        t2.start();
        
        await().atMost(Duration.ofMillis(500)).until(() -> 
            t2.getState() == Thread.State.BLOCKED || t2.getState() == Thread.State.WAITING);
            
        secondWait.countDown();
        
        t1.join();
        t2.join();
        
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, p1.getStatus());
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, p2.getStatus());
    }

    @Test
    void routeSupportsTimeBasedEviction() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        });

        ListUpdatePayload p1 = new ListUpdatePayload();
        p1.setAction(ActionType.UPDATE);
        p1.setItemId("item-1");

        service.route("list-1", p1);
        
        await().pollDelay(Duration.ofMillis(100)).until(() -> true);
        
        ListUpdatePayload p2 = new ListUpdatePayload();
        p2.setAction(ActionType.UPDATE);
        p2.setItemId("item-1");
        
        service.route("list-1", p2);
        
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, p2.getStatus());
    }
}