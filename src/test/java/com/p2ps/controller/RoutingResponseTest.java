package com.p2ps.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingResponseTest {

    @Test
    void shouldExposeConstructorValues() {
        List<RoutePoint> route = List.of(new RoutePoint("item_101", "Lapte", 47.151800, 27.588000));

        RoutingResponse response = new RoutingResponse();
        response.setStatus("success");
        response.setRoute(route);
        response.setWarnings(List.of());

        assertEquals("success", response.getStatus());
        assertEquals(route, response.getRoute());
    }

    @Test
    void shouldAllowUpdatingStatusAndRoute() {
        RoutingResponse response = new RoutingResponse();
        response.setStatus("success");
        response.setRoute(List.of());
        response.setWarnings(List.of());

        List<RoutePoint> updatedRoute = List.of(new RoutePoint("item_103", "Mere", 47.151900, 27.587950));

        response.setStatus("updated");
        response.setRoute(updatedRoute);

        assertEquals("updated", response.getStatus());
        assertEquals(updatedRoute, response.getRoute());
    }

    @Test
    void shouldExposeNewMetricsFields() {
        RoutingResponse response = new RoutingResponse();
        response.setTotalDistanceMeters(140.5);
        response.setEstimatedTimeSeconds(100);
        response.setTotalStops(5);

        assertEquals(140.5, response.getTotalDistanceMeters());
        assertEquals(100, response.getEstimatedTimeSeconds());
        assertEquals(5, response.getTotalStops());
    }
}