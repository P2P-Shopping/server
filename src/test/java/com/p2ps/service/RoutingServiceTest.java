package com.p2ps.service;

import com.p2ps.controller.RoutePoint;
import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutingServiceTest {

    private final RoutingService routingService = new RoutingService();

    @Test
    void calculateOptimalRoute_shouldReturnMockRouteUsingDefaultCoordinatesWhenRequestIsNull() {
        RoutingResponse response = routingService.calculateOptimalRoute(null);

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertEquals(4, response.getRoute().size());

        RoutePoint userPoint = response.getRoute().get(0);
        assertEquals("user_loc", userPoint.getItemId());
        assertEquals("Punctul Albastru (Tu)", userPoint.getName());
        assertEquals(47.151726, userPoint.getLat(), 0.000001);
        assertEquals(27.587914, userPoint.getLng(), 0.000001);

        RoutePoint lastPoint = response.getRoute().get(3);
        assertEquals("item_103", lastPoint.getItemId());
        assertEquals("Mere", lastPoint.getName());
    }

    @Test
    void calculateOptimalRoute_shouldUseRequestCoordinatesWhenRequestIsProvided() {
        RoutingRequest request = new RoutingRequest(10.5, 20.5, List.of("item_101", "item_102"));

        RoutingResponse response = routingService.calculateOptimalRoute(request);

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertEquals(4, response.getRoute().size());

        RoutePoint userPoint = response.getRoute().get(0);
        assertEquals("user_loc", userPoint.getItemId());
        assertEquals("Punctul Albastru (Tu)", userPoint.getName());
        assertEquals(10.5, userPoint.getLat(), 0.000001);
        assertEquals(20.5, userPoint.getLng(), 0.000001);
    }
}