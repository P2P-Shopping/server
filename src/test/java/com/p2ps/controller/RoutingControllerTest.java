package com.p2ps.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoutingControllerTest {

    @Test
    void shouldReturnSuccessStatusAndMockRouteWhenCalculateRouteIsCalled() {
        RoutingController controller = new RoutingController();
        RoutingRequest request = new RoutingRequest(47.151726, 27.587914, List.of("item_101", "item_102"));

        RoutingResponse response = controller.calculateRoute(request);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertEquals(4, response.getRoute().size());
        assertEquals("user_loc", response.getRoute().get(0).getItemId());
        assertEquals("Punctul Albastru (Tu)", response.getRoute().get(0).getName());
        assertEquals("item_103", response.getRoute().get(3).getItemId());
    }

    @Test
    void shouldReturnMockRouteEvenWhenRequestIsNull() {
        RoutingController controller = new RoutingController();

        RoutingResponse response = controller.calculateRoute(null);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertEquals(4, response.getRoute().size());
        assertEquals("user_loc", response.getRoute().get(0).getItemId());
        assertEquals("Mere", response.getRoute().get(3).getName());
    }
}
