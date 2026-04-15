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
}
