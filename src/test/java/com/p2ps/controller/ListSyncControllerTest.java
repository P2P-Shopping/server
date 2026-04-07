package com.p2ps.controller;

import com.p2ps.sync.service.ListSyncRouterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSyncControllerTest {

    @Mock
    private ListSyncRouterService listSyncRouterService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void handleListUpdate() {
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        when(listSyncRouterService.route("list-1", payload)).thenReturn(payload);

        ListSyncController controller = new ListSyncController(listSyncRouterService, messagingTemplate);

        ListUpdatePayload result = controller.handleListUpdate("list-1", null, payload);

        assertSame(payload, result);
        verify(listSyncRouterService).route("list-1", payload);
    }

    @Test
    void handleListUpdate_SendsRejectionToTheUser() {
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.CHECK_OFF);
        payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
        when(listSyncRouterService.route("list-1", payload)).thenReturn(payload);

        ListSyncController controller = new ListSyncController(listSyncRouterService, messagingTemplate);

        java.security.Principal principal = () -> "user-1";
        ListUpdatePayload result = controller.handleListUpdate("list-1", principal, payload);

        assertNull(result);
        verify(messagingTemplate).convertAndSendToUser("user-1", "/queue/list/list-1/rejection", payload);
        assertFalse(mockingDetails(messagingTemplate).getInvocations().stream().anyMatch(invocation ->
                invocation.getMethod().getName().equals("convertAndSend")
                        && "/topic/list/list-1".equals(invocation.getArgument(0))
                        && invocation.getArguments().length > 1
                        && invocation.getArgument(1) == null));
    }

    @Test
    void handleListUpdate_NullPayload_ThrowsException() {
        ListSyncController controller = new ListSyncController(listSyncRouterService, messagingTemplate);

        assertThrows(IllegalArgumentException.class, () ->
                controller.handleListUpdate("list-1", null, null));
    }
}
