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

    @Test
    void handleListUpdate() {
        ListSyncController controller = new ListSyncController(routerService);
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        when(routerService.route("list-1", payload)).thenReturn(payload);

        ListUpdatePayload result = controller.handleListUpdate("list-1", payload);

        assertSame(payload, result);
        verify(routerService).route("list-1", payload);
    }

    @Test
    void handleListUpdate_NullPayload_ThrowsException() {
        ListSyncController controller = new ListSyncController(routerService);

        assertThrows(IllegalArgumentException.class, () ->
                controller.handleListUpdate("list-1", null));
    }
}
