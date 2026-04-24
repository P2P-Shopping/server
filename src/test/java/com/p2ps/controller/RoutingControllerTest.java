package com.p2ps.controller;

import com.p2ps.model.StoreInventoryMap;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.service.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        routingService = mock(RoutingService.class);
        inventoryMapRepository = mock(StoreInventoryMapRepository.class);
        locationProcessorWorker = mock(LocationProcessorWorker.class);
        controller = new RoutingController(
                routingService,
                inventoryMapRepository,
                locationProcessorWorker,
                Duration.ofMinutes(1),
                10_000
        );
    }

    @Test
    void shouldReturnSuccessStatusAndMockRouteWhenCalculateRouteIsCalled() {
        RoutingRequest request = new RoutingRequest(47.151726, 27.587914, List.of("item_101", "item_102"));
        RoutingResponse mockResponse = new RoutingResponse(
                "success",
                List.of(
                        new RoutePoint("user_loc", "Punctul Albastru (Tu)", 47.151726, 27.587914),
                        new RoutePoint("item_101", "Lapte", 47.151800, 27.588000),
                        new RoutePoint("item_102", "Paine", 47.151850, 27.588050),
                        new RoutePoint("item_103", "Mere", 47.151900, 27.588100)
                ),
                List.of()
        );
        when(routingService.calculateOptimalRoute(request)).thenReturn(mockResponse);

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
        RoutingRequest request = new RoutingRequest(47.151726, 27.587914, List.of());
        RoutingResponse mockResponse = new RoutingResponse("success", List.of(
                new RoutePoint("user_loc", "Tu", 47.151726, 27.587914),
                new RoutePoint("item_101", "Lapte", 47.151800, 27.588000),
                new RoutePoint("item_102", "Paine", 47.151850, 27.588050),
                new RoutePoint("item_103", "Mere", 47.151900, 27.588100)
        ), List.of());
        when(routingService.calculateOptimalRoute(request)).thenReturn(mockResponse);

        RoutingResponse response = controller.calculateRoute(request);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
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
        map.setLastUpdated(LocalDateTime.now().minusMinutes(5));
        Point estimatedPoint = mock(Point.class);
        when(estimatedPoint.getCoordinate()).thenReturn(new Coordinate(27.587914, 47.151726));
        map.setEstimatedLocPoint(estimatedPoint);

        when(inventoryMapRepository.findByStoreIdAndItemId(storeId, itemId)).thenReturn(Optional.of(map));
        when(locationProcessorWorker.isLowConfidence(0.2d, 2)).thenReturn(true);
        when(locationProcessorWorker.recalculateSingleItem(storeId, itemId)).thenReturn(CompletableFuture.completedFuture(null));

        ResponseEntity<com.p2ps.dto.ItemLocationDTO> response = controller.getItemLocation(storeId, itemId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(47.151726, response.getBody().getLat(), 0.000001);
        assertEquals(27.587914, response.getBody().getLon(), 0.000001);
        assertTrue(response.getBody().isLowConfidenceWarning());
        assertEquals(0.2d, response.getBody().getConfidenceScore(), 0.000001);
        verify(locationProcessorWorker).recalculateSingleItem(storeId, itemId);
    }

    @Test
    void shouldReturnNoContentWhenCoordinateIsMissing() {
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        StoreInventoryMap map = new StoreInventoryMap();
        map.setStoreId(storeId);
        map.setItemId(itemId);
        map.setConfidenceScore(0.9d);
        map.setPingCount(10);
        map.setLastUpdated(LocalDateTime.now());

        when(inventoryMapRepository.findByStoreIdAndItemId(storeId, itemId)).thenReturn(Optional.of(map));
        when(locationProcessorWorker.isLowConfidence(0.9d, 10)).thenReturn(false);

        ResponseEntity<com.p2ps.dto.ItemLocationDTO> response = controller.getItemLocation(storeId, itemId);

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void shouldReturnNoContentWhenEstimatedPointHasNullCoordinate() {
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        StoreInventoryMap map = new StoreInventoryMap();
        map.setStoreId(storeId);
        map.setItemId(itemId);
        map.setConfidenceScore(0.95d);
        map.setPingCount(12);
        map.setLastUpdated(LocalDateTime.now());

        Point estimatedPoint = mock(Point.class);
        when(estimatedPoint.getCoordinate()).thenReturn(null);
        map.setEstimatedLocPoint(estimatedPoint);

        when(inventoryMapRepository.findByStoreIdAndItemId(storeId, itemId)).thenReturn(Optional.of(map));
        when(locationProcessorWorker.isLowConfidence(0.95d, 12)).thenReturn(false);

        ResponseEntity<com.p2ps.dto.ItemLocationDTO> response = controller.getItemLocation(storeId, itemId);

        assertEquals(204, response.getStatusCode().value());
    }

    @Test
    void shouldTriggerRecalculationOnlyOnceWithinCooldownWindow() {
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        StoreInventoryMap map = new StoreInventoryMap();
        map.setStoreId(storeId);
        map.setItemId(itemId);
        map.setConfidenceScore(0.1d);
        map.setPingCount(1);
        map.setLastUpdated(LocalDateTime.now().minusMinutes(5));
        Point estimatedPoint = mock(Point.class);
        when(estimatedPoint.getCoordinate()).thenReturn(new Coordinate(27.587914, 47.151726));
        map.setEstimatedLocPoint(estimatedPoint);

        when(inventoryMapRepository.findByStoreIdAndItemId(storeId, itemId)).thenReturn(Optional.of(map));
        when(locationProcessorWorker.isLowConfidence(0.1d, 1)).thenReturn(true);
        when(locationProcessorWorker.recalculateSingleItem(storeId, itemId)).thenReturn(CompletableFuture.completedFuture(null));

        controller.getItemLocation(storeId, itemId);
        controller.getItemLocation(storeId, itemId);

        verify(locationProcessorWorker).recalculateSingleItem(storeId, itemId);
    }
}
