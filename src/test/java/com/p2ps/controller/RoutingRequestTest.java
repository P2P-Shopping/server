package com.p2ps.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RoutingRequestTest {

    @Test
    void shouldStoreValuesProvidedInConstructor() {
        RoutingRequest request = new RoutingRequest(47.151726, 27.587914, List.of("item_101", "item_102"));

        assertEquals(47.151726, request.getUserLat());
        assertEquals(27.587914, request.getUserLng());
        assertEquals(List.of("item_101", "item_102"), request.getProductIds());
    }

    @Test
    void shouldAllowUpdatingFieldsThroughSetters() {
        RoutingRequest request = new RoutingRequest();

        request.setUserLat(45.0);
        request.setUserLng(25.5);
        request.setProductIds(List.of("item_201"));

        assertEquals(45.0, request.getUserLat());
        assertEquals(25.5, request.getUserLng());
        assertEquals(List.of("item_201"), request.getProductIds());
    }

    @Test
    void shouldAllowNullProductIdsWhenNoItemsAreProvided() {
        RoutingRequest request = new RoutingRequest(0.0, 0.0, null);

        assertNull(request.getProductIds());
    }

    @Test
    void equalsAndHashCode_ForIdenticalValues() {
        RoutingRequest r1 = new RoutingRequest(47.151726, 27.587914, List.of("item_101", "item_102"));
        RoutingRequest r2 = new RoutingRequest(47.151726, 27.587914, List.of("item_101", "item_102"));

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void equalsHandlesNullAndDifferentClass() {
        RoutingRequest r = new RoutingRequest(1.0, 2.0, List.of("a"));

        assertFalse(r.equals(null));
        assertFalse(r.equals("not a routing request"));
    }

    @Test
    void equalsAndHashCode_WithNullProductIds() {
        RoutingRequest a = new RoutingRequest(1.0, 2.0, null);
        RoutingRequest b = new RoutingRequest(1.0, 2.0, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        RoutingRequest c = new RoutingRequest(1.0, 2.0, List.of());
        // null vs empty list should not be equal
        assertNotEquals(a, c);
    }
}
