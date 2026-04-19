package com.p2ps.controller;

import com.p2ps.service.RoutingService;
import com.p2ps.repository.StoreInventoryMapRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoutingControllerTest {

    @Test
    void shouldReturnSuccessStatusAndMockRouteWhenCalculateRouteIsCalled() {
        RoutingService routingService = new RoutingService();
        StoreInventoryMapRepository inventoryMapRepository = Mockito.mock(StoreInventoryMapRepository.class);
        RoutingController controller = new RoutingController(routingService, inventoryMapRepository);
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
        RoutingService routingService = new RoutingService();
        StoreInventoryMapRepository inventoryMapRepository = Mockito.mock(StoreInventoryMapRepository.class);
        RoutingController controller = new RoutingController(routingService, inventoryMapRepository);
        RoutingResponse response = controller.calculateRoute(null);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertEquals(4, response.getRoute().size());
        assertEquals("user_loc", response.getRoute().get(0).getItemId());
        assertEquals("Mere", response.getRoute().get(3).getName());
    }
}