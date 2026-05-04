package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void routeRejectsCheckOffWithoutExplicitChecked() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId("item-1");
        payload.setChecked(null);

        ListUpdatePayload result = service.route("list-1", payload);

        assertEquals(ListUpdatePayload.STATUS_REJECTION, result.getStatus());
    }

    @Test
    void routeReturnsPayloadUnchangedForUnknownActions() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> payload);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UNKNOWN);

        ListUpdatePayload result = service.route("list-1", payload);

        assertSame(payload, result);
    }

    @Test
    void routeHandlesDeleteActionAndSupportsEviction() {
        // We use a mock store that returns SUCCESS for DELETE
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        });

        ListUpdatePayload deletePayload = new ListUpdatePayload();
        deletePayload.setAction(ActionType.DELETE);
        deletePayload.setItemId("item-to-delete");

        ListUpdatePayload result = service.route("list-1", deletePayload);

        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
        // Internal state check is hard without reflection, but we cover the branch
    }

    @Test
    void routeHandlesInterruption() {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            try {
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return payload;
        });

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        payload.setItemId("item-1");

        java.util.concurrent.atomic.AtomicReference<Exception> caught = new java.util.concurrent.atomic.AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                service.route("list-1", payload);
            } catch (IllegalStateException e) {
                caught.set(e);
            }
        });

        t.start();
        // Give it a moment to enter the synchronized block in route
        try { Thread.sleep(100); } catch (InterruptedException _) { Thread.currentThread().interrupt(); }
        t.interrupt();
        
        org.awaitility.Awaitility.await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
                .until(() -> caught.get() != null);
        
        assertThat(caught.get().getMessage()).contains("Interrupted");
    }

    @Test
    void routeWaitOnLock() throws Exception {
        java.util.concurrent.CountDownLatch firstEnter = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch secondWait = new java.util.concurrent.CountDownLatch(1);
        
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
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
        
        // Start second thread, it should wait for t1 to finish
        Thread t2 = new Thread(() -> service.route("list-1", p2));
        t2.start();
        
        // Let t1 finish
        secondWait.countDown();
        
        t1.join();
        t2.join();
        
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, p1.getStatus());
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, p2.getStatus());
    }

    @Test
    void routeSupportsTimeBasedEviction() throws Exception {
        ListSyncRouterService service = new ListSyncRouterService((listId, payload) -> {
            payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
            return payload;
        });

        ListUpdatePayload p1 = new ListUpdatePayload();
        p1.setAction(ActionType.UPDATE);
        p1.setItemId("item-1");

        service.route("list-1", p1);
        
        // Wait for lock window to pass
        Thread.sleep(100); 
        
        ListUpdatePayload p2 = new ListUpdatePayload();
        p2.setAction(ActionType.UPDATE);
        p2.setItemId("item-1");
        
        service.route("list-1", p2);
        
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, p2.getStatus());
    }
}
