package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.service.ItemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseListSyncStoreTest {

    @Mock
    private ItemService itemService;

    @Test
    void appliesCheckedUpdatesThroughItemService() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        UUID itemId = UUID.randomUUID();

        ItemDTO updated = new ItemDTO();
        updated.setChecked(true);
        updated.setLastUpdatedTimestamp(123L);

        when(itemService.updateItemStatus(any(UUID.class), any(boolean.class), any())).thenReturn(updated);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId(itemId.toString());
        payload.setTimestamp(100L);

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals(Boolean.TRUE, result.getChecked());
        assertEquals(123L, result.getTimestamp());
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
    }

    @Test
    void rejectsWhenItemServiceThrowsOptimisticLockingFailure() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        UUID itemId = UUID.randomUUID();

        when(itemService.updateItemStatus(any(UUID.class), any(boolean.class), any()))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId(itemId.toString());

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals(ListUpdatePayload.STATUS_REJECTION, result.getStatus());
    }

    @Test
    void rejectsInvalidUuidItemId() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId("not-a-uuid");
        payload.setChecked(Boolean.TRUE);

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals(ListUpdatePayload.STATUS_REJECTION, result.getStatus());
    }

    @Test
    void returnsEarlyForBlankListIdAndNullPayload() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        payload.setItemId(UUID.randomUUID().toString());

        assertSame(payload, store.apply(null, payload));
        assertSame(payload, store.apply("", payload));
        assertNull(store.apply("list-1", null));
    }

    @Test
    void returnsEarlyWhenCheckedMissingForNonCheckOffAction() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.ADD);
        payload.setItemId(UUID.randomUUID().toString());

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertNull(result.getStatus());
    }

    @Test
    void returnsEarlyWhenItemIdIsMissing() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);

        ListUpdatePayload p1 = new ListUpdatePayload();
        p1.setItemId(null);
        assertSame(p1, store.apply("list-1", p1));

        ListUpdatePayload p2 = new ListUpdatePayload();
        p2.setItemId("");
        assertSame(p2, store.apply("list-1", p2));
    }
}
