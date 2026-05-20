package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.service.ItemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        when(itemService.updateItemFromSync(any(UUID.class), any())).thenReturn(updated);

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

        when(itemService.updateItemFromSync(any(UUID.class), any()))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("conflict"));

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
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
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

    @Test
    void handlesAddAndDeleteActionsWithoutItemService() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        
        ListUpdatePayload addPayload = new ListUpdatePayload();
        addPayload.setAction(ActionType.ADD);
        addPayload.setItemId(UUID.randomUUID().toString());
        
        ListUpdatePayload addResult = store.apply("list-1", addPayload);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, addResult.getStatus());
        
        ListUpdatePayload deletePayload = new ListUpdatePayload();
        deletePayload.setAction(ActionType.DELETE);
        deletePayload.setItemId(UUID.randomUUID().toString());
        
        ListUpdatePayload deleteResult = store.apply("list-1", deletePayload);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, deleteResult.getStatus());
        
        // Verify itemService was never called for these
        org.mockito.Mockito.verifyNoInteractions(itemService);
    }

    @Test
    void returnsEarlyForUnknownAction() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UNKNOWN);
        payload.setItemId(UUID.randomUUID().toString());
        
        assertSame(payload, store.apply("list-1", payload));
        assertNull(payload.getStatus());
        
        org.mockito.Mockito.verifyNoInteractions(itemService);
    }

    @Test
    void returnsEarlyForNullAction() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(null);
        payload.setItemId(UUID.randomUUID().toString());
        
        assertSame(payload, store.apply("list-1", payload));
        
        org.mockito.Mockito.verifyNoInteractions(itemService);
    }

    @Test
    void returnsEarlyForBlankListId() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        ListUpdatePayload payload = new ListUpdatePayload();
        
        assertSame(payload, store.apply("   ", payload));
    }

    @Test
    void claimItemSetsClaimedByAndTimestamp() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        UUID itemId = UUID.randomUUID();

        ItemDTO updated = new ItemDTO();
        updated.setClaimedBy("alice@test.com");
        updated.setChecked(false);
        updated.setLastUpdatedTimestamp(456L);

        when(itemService.claimItem(itemId, "alice@test.com")).thenReturn(updated);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CLAIM_ITEM);
        payload.setItemId(itemId.toString());
        payload.setClaimedBy("alice@test.com");

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals("alice@test.com", result.getClaimedBy());
        assertEquals(456L, result.getTimestamp());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("alice@test.com"));
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
    }

    @Test
    void unclaimItemClearsClaimedBy() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        UUID itemId = UUID.randomUUID();

        ItemDTO updated = new ItemDTO();
        updated.setClaimedBy(null);
        updated.setChecked(false);
        updated.setLastUpdatedTimestamp(789L);

        when(itemService.claimItem(itemId, null)).thenReturn(updated);

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UNCLAIM_ITEM);
        payload.setItemId(itemId.toString());

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertNull(result.getClaimedBy());
        assertEquals(789L, result.getTimestamp());
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, result.getStatus());
    }

    @Test
    void claimItemRejectsOnException() {
        DatabaseListSyncStore store = new DatabaseListSyncStore(itemService);
        UUID itemId = UUID.randomUUID();

        when(itemService.claimItem(any(UUID.class), any()))
                .thenThrow(new com.p2ps.lists.exception.ItemNotFoundException("not found"));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CLAIM_ITEM);
        payload.setItemId(itemId.toString());
        payload.setClaimedBy("alice@test.com");

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals(ListUpdatePayload.STATUS_REJECTION, result.getStatus());
    }
}
