package com.p2ps.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
