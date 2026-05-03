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

@ExtendWith(MockitoExtension.class)
class ListSyncControllerTest {

    @Mock
    private ListSyncRouterService routerService;

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Test
    void handleListUpdate() {
        ListSyncController controller = new ListSyncController(routerService, messagingTemplate);
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        when(routerService.route("list-1", payload)).thenReturn(payload);

        controller.handleListUpdate("list-1", payload);

        verify(routerService).route("list-1", payload);
        verify(messagingTemplate).convertAndSend("/topic/list/list-1", payload);
    }

    @Test
    void handleListUpdate_NullPayload_NoException() {
        ListSyncController controller = new ListSyncController(routerService, messagingTemplate);

        // Should just return silently as per implementation
        controller.handleListUpdate("list-1", null);
    }
}
