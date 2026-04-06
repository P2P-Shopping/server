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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertTrue(Boolean.TRUE.equals(payload.getChecked()));

        ArgumentCaptor<RoomItemState> captor = ArgumentCaptor.forClass(RoomItemState.class);
        verify(repository).save(captor.capture());

        RoomItemState saved = captor.getValue();
        assertEquals("list-1", saved.getListId());
        assertEquals("item-1", saved.getItemId());
        assertTrue(saved.isChecked());
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
}