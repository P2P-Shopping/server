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
import static org.mockito.Mockito.atLeastOnce;
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
        assertEquals("list-1", saved.getListId());
        assertEquals("item-1", saved.getItemId());
        assertEquals(true, saved.isChecked());
    }

    @Test
    void updatesExistingStateAndReturnsThePayload() {
        RoomItemState existing = new RoomItemState("list-1", "item-2");
        existing.setContent("Old");
        existing.setChecked(false);
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
        assertEquals("list-1", saved.getListId());
        assertEquals("item-2", saved.getItemId());
        assertEquals("New", saved.getContent());
        assertEquals(false, saved.isChecked());
    }

    @Test
    void respectsExplicitCheckedFalseValueWithoutToggling() {
        RoomItemState existing = new RoomItemState("list-1", "item-3");
        existing.setChecked(true);
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
        assertEquals(false, captor.getValue().isChecked());
    }

    @Test
    void respectsExplicitCheckedTrueValueWithoutToggling() {
        RoomItemState existing = new RoomItemState("list-1", "item-4");
        existing.setChecked(false);
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
        assertEquals(true, captor.getValue().isChecked());
    }

    @Test
    void deletesExistingStateForDeleteActions() {
        RoomItemState existing = new RoomItemState("list-1", "item-9");
        when(repository.findByListIdAndItemId("list-1", "item-9")).thenReturn(Optional.of(existing));
        when(repository.save(any(RoomItemState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.DELETE);
        payload.setItemId("item-9");

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        ArgumentCaptor<RoomItemState> captor = ArgumentCaptor.forClass(RoomItemState.class);
        verify(repository).save(captor.capture());
        assertEquals(true, captor.getValue().isDeleted());
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
        existing.setContent("Old");
        existing.setChecked(true);
        when(repository.findByListIdAndItemId("list-1", "item-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(RoomItemState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setItemId("item-1");
        payload.setContent("New");

        ListUpdatePayload result = store.apply("list-1", payload);

        assertSame(payload, result);
        assertEquals("New", payload.getContent());
        assertEquals(false, payload.getChecked());
        verify(repository).save(any(RoomItemState.class));
    }
}
