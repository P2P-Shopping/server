package com.p2ps.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingResponseTest {

    @Test
    void shouldExposeConstructorValues() {
        List<RoutePoint> route = List.of(new RoutePoint("item_101", "Lapte", 47.151800, 27.588000));
        RoutingResponse response = new RoutingResponse("success", route, List.of());

        assertEquals("success", response.getStatus());
        assertEquals(route, response.getRoute());
    }

    @Test
    void shouldAllowUpdatingStatusAndRoute() {
        RoutingResponse response = new RoutingResponse("success", List.of(), List.of());
        List<RoutePoint> updatedRoute = List.of(new RoutePoint("item_103", "Mere", 47.151900, 27.587950));

        response.setStatus("updated");
        response.setRoute(updatedRoute);

        assertEquals("updated", response.getStatus());
        assertEquals(updatedRoute, response.getRoute());
    }
}
