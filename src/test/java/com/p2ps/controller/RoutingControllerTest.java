package com.p2ps.controller;

import com.p2ps.service.RoutingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingControllerTest {

    @Mock
    private RoutingService routingService;

    @InjectMocks
    private RoutingController controller;

    @Test
    void shouldReturnSuccessStatusWhenCalculateRouteIsCalled() {
        RoutingRequest request = new RoutingRequest(47.151726, 27.587914, List.of("item_101", "item_102"));
        List<RoutePoint> mockRoute = List.of(
                new RoutePoint("user_loc", "Tu", 47.151726, 27.587914),
                new RoutePoint("item_101", "Lapte", 47.151800, 27.588000)
        );
        RoutingResponse mockResponse = new RoutingResponse("success", mockRoute, List.of());
        when(routingService.calculateOptimalRoute(request)).thenReturn(mockResponse);

        RoutingResponse response = controller.calculateRoute(request);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertEquals(2, response.getRoute().size());
        assertEquals("user_loc", response.getRoute().get(0).getItemId());
    }

    @Test
    void shouldDelegateToRoutingService() {
        RoutingRequest request = new RoutingRequest(47.151726, 27.587914, List.of("item_101"));
        RoutingResponse mockResponse = new RoutingResponse("error", List.of(), List.of("Nu esti in niciun magazin."));
        when(routingService.calculateOptimalRoute(request)).thenReturn(mockResponse);

        RoutingResponse response = controller.calculateRoute(request);

        assertEquals("error", response.getStatus());
        assertNotNull(response.getWarnings());
    }
}
