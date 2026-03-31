package com.p2ps.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutePointTest {

    @Test
    void shouldExposeConstructorValues() {
        RoutePoint point = new RoutePoint("item_101", "Lapte", 47.151800, 27.588000);

        assertEquals("item_101", point.getItemId());
        assertEquals("Lapte", point.getName());
        assertEquals(47.151800, point.getLat());
        assertEquals(27.588000, point.getLng());
    }

    @Test
    void shouldAllowUpdatingRoutePointFields() {
        RoutePoint point = new RoutePoint("item_101", "Lapte", 47.151800, 27.588000);

        point.setItemId("item_102");
        point.setName("Paine");
        point.setLat(47.151850);
        point.setLng(27.588150);

        assertEquals("item_102", point.getItemId());
        assertEquals("Paine", point.getName());
        assertEquals(47.151850, point.getLat());
        assertEquals(27.588150, point.getLng());
    }
}
