package com.p2ps.controller;

import com.p2ps.model.StoreInventoryMap;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.service.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingControllerTest {

    private RoutingService routingService;
    private StoreInventoryMapRepository inventoryMapRepository;
    private LocationProcessorWorker locationProcessorWorker;
    private RoutingController controller;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService();
        inventoryMapRepository = Mockito.mock(StoreInventoryMapRepository.class);
        locationProcessorWorker = mock(LocationProcessorWorker.class);
        controller = new RoutingController(routingService, inventoryMapRepository, locationProcessorWorker);
    }

    @Test
    void shouldReturnSuccessStatusAndMockRouteWhenCalculateRouteIsCalled() {
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
        RoutingResponse response = controller.calculateRoute(null);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertEquals(4, response.getRoute().size());
        assertEquals("user_loc", response.getRoute().get(0).getItemId());
        assertEquals("Mere", response.getRoute().get(3).getName());
    }

    @Test
    void shouldReturnLocationWithWarningAndTriggerRapidRecalculationForLowConfidenceItem() {
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        StoreInventoryMap map = new StoreInventoryMap();
        map.setStoreId(storeId);
        map.setItemId(itemId);
        map.setConfidenceScore(0.2d);
        map.setPingCount(2);
        map.setEstimatedLocPoint(Mockito.mock(org.locationtech.jts.geom.Point.class));
        Mockito.when(map.getEstimatedLocPoint().getCoordinate()).thenReturn(new org.locationtech.jts.geom.Coordinate(27.587914, 47.151726));

        when(inventoryMapRepository.findByStoreIdAndItemId(storeId, itemId)).thenReturn(Optional.of(map));
        when(locationProcessorWorker.isLowConfidence(0.2d, 2)).thenReturn(true);
        when(locationProcessorWorker.recalculateSingleItem(storeId, itemId)).thenReturn(CompletableFuture.completedFuture(null));

        ResponseEntity<com.p2ps.dto.ItemLocationDTO> response = controller.getItemLocation(storeId, itemId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(47.151726, response.getBody().getLat(), 0.000001);
        assertEquals(27.587914, response.getBody().getLon(), 0.000001);
        assertEquals(true, response.getBody().isLowConfidenceWarning());
        assertEquals(0.2d, response.getBody().getConfidenceScore(), 0.000001);
        verify(locationProcessorWorker).recalculateSingleItem(storeId, itemId);
    }
}
