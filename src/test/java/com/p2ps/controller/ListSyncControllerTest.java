package com.p2ps.controller;

import com.p2ps.sync.service.ListSyncRouterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.List;
import java.util.Arrays;

@ExtendWith(MockitoExtension.class)
class ListSyncControllerTest {

    @Mock
    private ListSyncRouterService routerService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void handleListUpdate() {
        ListSyncController controller = new ListSyncController(routerService, messagingTemplate);
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        when(routerService.route("list-1", payload)).thenReturn(payload);

        ListUpdatePayload result = controller.handleListUpdate("list-1", payload);

        assertSame(payload, result);
        verify(routerService).route("list-1", payload);
    }

    @Test
    void handleListUpdate_NullPayload_ThrowsException() {
        ListSyncController controller = new ListSyncController(routerService, messagingTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                controller.handleListUpdate("list-1", null));
    }

    @Test
    void handleBatchUpdate() {
        ListSyncController controller = new ListSyncController(routerService, messagingTemplate);
        ListUpdatePayload p1 = new ListUpdatePayload();
        ListUpdatePayload p2 = new ListUpdatePayload();
        List<ListUpdatePayload> payloads = Arrays.asList(p1, p2);

        when(routerService.routeBatch("list-1", payloads)).thenReturn(payloads);

        controller.handleBatchUpdate("list-1", payloads);

        verify(routerService).routeBatch("list-1", payloads);
        verify(messagingTemplate).convertAndSend("/topic/list/list-1", p1);
        verify(messagingTemplate).convertAndSend("/topic/list/list-1", p2);
    }

    @Test
    void handleBatchUpdate_IsolatesFailures() {
        ListSyncController controller = new ListSyncController(routerService, messagingTemplate);
        ListUpdatePayload p1 = new ListUpdatePayload();
        ListUpdatePayload p2 = new ListUpdatePayload();
        List<ListUpdatePayload> payloads = Arrays.asList(p1, p2);

        when(routerService.routeBatch("list-1", payloads)).thenReturn(payloads);
        doThrow(new RuntimeException("Failure")).when(messagingTemplate).convertAndSend("/topic/list/list-1", p1);

        controller.handleBatchUpdate("list-1", payloads);

        verify(messagingTemplate).convertAndSend("/topic/list/list-1", p1);
        verify(messagingTemplate).convertAndSend("/topic/list/list-1", p2);
    }

    @Test
    void handleBatchUpdate_BlankListId_ThrowsException() {
        ListSyncController controller = new ListSyncController(routerService, messagingTemplate);
        List<ListUpdatePayload> payloads = Arrays.asList(new ListUpdatePayload());
        assertThrows(IllegalArgumentException.class, () ->
                controller.handleBatchUpdate(" ", payloads));
    }

    @Test
    void handleBatchUpdate_EmptyPayloads_ReturnsEarly() {
        ListSyncController controller = new ListSyncController(routerService, messagingTemplate);
        controller.handleBatchUpdate("list-1", null);
        controller.handleBatchUpdate("list-1", Arrays.asList());
        // Verify no routing happens
        verify(routerService, org.mockito.Mockito.never()).routeBatch(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }
}
