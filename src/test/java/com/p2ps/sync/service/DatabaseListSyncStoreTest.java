package com.p2ps.sync.service;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.sync.model.RoomItemState;
import com.p2ps.sync.repository.RoomItemStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseListSyncStoreTest {

    @Mock
    private RoomItemStateRepository repository;

    private DatabaseListSyncStore store;

    @BeforeEach
    void setUp() {
        store = new DatabaseListSyncStore(repository);
    }

    @Test
    void appliesCheckOffUpdatesToTheDatabase() {
        when(repository.findByListIdAndItemId("list-1", "item-1")).thenReturn(Optional.empty());
        when(repository.save(any(RoomItemState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId("item-1");

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals(Boolean.TRUE, payload.getChecked());

        ArgumentCaptor<RoomItemState> captor = ArgumentCaptor.forClass(RoomItemState.class);
        verify(repository).save(captor.capture());

        RoomItemState saved = captor.getValue();
        assertEquals("list-1", readStateValue(saved, "listId"));
        assertEquals("item-1", readStateValue(saved, "itemId"));
        assertEquals(Boolean.TRUE, readStateValue(saved, "checked"));
    }

    @Test
    void updatesExistingStateAndReturnsThePayload() {
        RoomItemState existing = new RoomItemState("list-1", "item-2");
        setStateValue(existing, "content", "Old");
        setStateValue(existing, "checked", false);
        when(repository.findByListIdAndItemId("list-1", "item-2")).thenReturn(Optional.of(existing));
        when(repository.save(any(RoomItemState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        payload.setItemId("item-2");
        payload.setContent("New");

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals("New", payload.getContent());
        assertEquals(Boolean.FALSE, payload.getChecked());

        ArgumentCaptor<RoomItemState> captor = ArgumentCaptor.forClass(RoomItemState.class);
        verify(repository).save(captor.capture());
        RoomItemState saved = captor.getValue();
        assertEquals("list-1", readStateValue(saved, "listId"));
        assertEquals("item-2", readStateValue(saved, "itemId"));
        assertEquals("New", readStateValue(saved, "content"));
        assertEquals(Boolean.FALSE, readStateValue(saved, "checked"));
    }

    @Test
    void respectsExplicitCheckedFalseValueWithoutToggling() {
        RoomItemState existing = new RoomItemState("list-1", "item-3");
        setStateValue(existing, "checked", true);
        when(repository.findByListIdAndItemId("list-1", "item-3")).thenReturn(Optional.of(existing));
        when(repository.save(any(RoomItemState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId("item-3");
        payload.setChecked(Boolean.FALSE);

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals(Boolean.FALSE, payload.getChecked());

        ArgumentCaptor<RoomItemState> captor = ArgumentCaptor.forClass(RoomItemState.class);
        verify(repository).save(captor.capture());
        assertEquals(Boolean.FALSE, readStateValue(captor.getValue(), "checked"));
    }

    @Test
    void respectsExplicitCheckedTrueValueWithoutToggling() {
        RoomItemState existing = new RoomItemState("list-1", "item-4");
        setStateValue(existing, "checked", false);
        when(repository.findByListIdAndItemId("list-1", "item-4")).thenReturn(Optional.of(existing));
        when(repository.save(any(RoomItemState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId("item-4");
        payload.setChecked(Boolean.TRUE);

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals(Boolean.TRUE, payload.getChecked());

        ArgumentCaptor<RoomItemState> captor = ArgumentCaptor.forClass(RoomItemState.class);
        verify(repository).save(captor.capture());
        assertEquals(Boolean.TRUE, readStateValue(captor.getValue(), "checked"));
    }

    @Test
    void deletesExistingStateForDeleteActions() {
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.DELETE);
        payload.setItemId("item-9");

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        verify(repository).deleteByListIdAndItemId("list-1", "item-9");
        verify(repository, never()).save(any());
    }

    @Test
    void ignoresPersistentActionsWithoutAnItemId() {
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        verifyNoInteractions(repository);
    }

    @Test
    void ignoresBlankListIdsAndNullPayloads() {
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.ADD);
        payload.setItemId("item-1");

        assertSame(payload, store.apply(" ", payload));
        assertNull(store.apply("list-1", null));
        verifyNoInteractions(repository);
    }

    @Test
    void updatesExistingStateContentAndTogglesCheckedWhenPayloadOmitsIt() {
        RoomItemState existing = new RoomItemState("list-1", "item-1");
        setStateValue(existing, "content", "Old");
        setStateValue(existing, "checked", true);
        when(repository.findByListIdAndItemId("list-1", "item-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(RoomItemState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId("item-1");
        payload.setContent("New");

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals("New", payload.getContent());
        assertEquals(Boolean.FALSE, payload.getChecked());
        verify(repository).save(any(RoomItemState.class));
        verify(repository, never()).deleteByListIdAndItemId("list-1", "item-1");
    }

    private static Object readStateValue(RoomItemState state, String fieldName) {
        try {
            var field = RoomItemState.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(state);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void setStateValue(RoomItemState state, String fieldName, Object value) {
        try {
            var field = RoomItemState.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(state, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
