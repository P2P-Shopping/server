package com.p2ps.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoutingRequestTest {

    private static RoutingRequest request(double userLat, double userLng, List<String> productIds) {
        RoutingRequest request = new RoutingRequest();
        request.setUserLat(userLat);
        request.setUserLng(userLng);
        request.setProductIds(productIds);
        return request;
    }

    @Test
    void shouldStoreValuesProvidedInConstructor() {
        RoutingRequest request = request(47.151726, 27.587914, List.of("item_101", "item_102"));

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
        RoutingRequest request = request(0.0, 0.0, null);

        assertNull(request.getProductIds());
    }

    @Test
    void equalsAndHashCode_ForIdenticalValues() {
        RoutingRequest r1 = request(47.151726, 27.587914, List.of("item_101", "item_102"));
        RoutingRequest r2 = request(47.151726, 27.587914, List.of("item_101", "item_102"));

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void equalsHandlesNullAndDifferentClass() {
        RoutingRequest r = request(1.0, 2.0, List.of("a"));

        assertNotEquals(r, null);
        assertNotEquals(r, "not a routing request");
    }

    @Test
    void equalsAndHashCode_WithNullProductIds() {
        RoutingRequest a = request(1.0, 2.0, null);
        RoutingRequest b = request(1.0, 2.0, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        RoutingRequest c = request(1.0, 2.0, List.of());
        // null vs empty list should not be equal
        assertNotEquals(a, c);
    }
}
