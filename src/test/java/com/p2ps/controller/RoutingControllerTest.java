package com.p2ps.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.model.StoreInventoryMap;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.service.MacroRoutingService;
import com.p2ps.service.RoutingService;
import com.p2ps.service.StoreMatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingControllerTest {

    private RoutingService routingService;
    private MacroRoutingService macroRoutingService;
    private StoreInventoryMapRepository inventoryMapRepository;
    private LocationProcessorWorker locationProcessorWorker;
    private StoreMatchingEngine storeMatchingEngine;
    private StringRedisTemplate redis;
    private ObjectMapper objectMapper;

    private RoutingController controller;

    @BeforeEach
    void setUp() {
        routingService = mock(RoutingService.class);
        macroRoutingService = mock(MacroRoutingService.class);
        inventoryMapRepository = mock(StoreInventoryMapRepository.class);
        locationProcessorWorker = mock(LocationProcessorWorker.class);
        storeMatchingEngine = mock(StoreMatchingEngine.class);
        redis = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();

        controller = new RoutingController(
                routingService,
                macroRoutingService,
                inventoryMapRepository,
                locationProcessorWorker,
                storeMatchingEngine,
                redis,
                objectMapper
        );
        // Manually trigger @PostConstruct for the test
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "recalculationCooldown", Duration.ofMinutes(1));
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "recalculationGuardMaxSize", 10000);
        controller.init();
    }

    @Test
    void shouldReturnSuccessStatusAndMockRouteWhenCalculateRouteIsCalled() {
        RoutingRequest request = new RoutingRequest(47.151726, 27.587914, List.of("item_101", "item_102"), 0);

        RoutingResponse mockResponse = new RoutingResponse();
        mockResponse.setStatus("success");
        mockResponse.setRoute(List.of(
                new RoutePoint("user_loc", "Punctul Albastru (Tu)", 47.151726, 27.587914),
                new RoutePoint("item_101", "Lapte", 47.151800, 27.588000),
                new RoutePoint("item_102", "Paine", 47.151850, 27.588050),
                new RoutePoint("item_103", "Mere", 47.151900, 27.588100)
        ));
        mockResponse.setWarnings(List.of());
        mockResponse.setPartial(false);

        when(routingService.calculateOptimalRoute(request)).thenReturn(mockResponse);

        RoutingResponse response = controller.calculateRoute(request);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertEquals(4, response.getRoute().size());
        assertEquals("user_loc", response.getRoute().getFirst().getItemId());
        assertFalse(response.isPartial());
    }

    @Test
    void shouldReturnMockRouteForEmptyItemList() {
        RoutingRequest request = new RoutingRequest(47.151726, 27.587914, List.of(), 0);

        RoutingResponse mockResponse = new RoutingResponse();
        mockResponse.setStatus("success");
        mockResponse.setRoute(List.of(
                new RoutePoint("user_loc", "Tu", 47.151726, 27.587914)
        ));
        mockResponse.setWarnings(List.of());

        when(routingService.calculateOptimalRoute(request)).thenReturn(mockResponse);

        RoutingResponse response = controller.calculateRoute(request);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
    }

    @Test
    void lookupStores_shouldReturn200AndTopStoresWhenMatchesExist() {
        StoreMatchRequest request = new StoreMatchRequest();
        request.setUserLat(47.15);
        request.setUserLng(27.58);
        request.setRadiusInMeters(5000);
        request.setItemIds(List.of(UUID.randomUUID(), UUID.randomUUID()));

        List<StoreMatchingEngine.StoreMatchResult> matches = List.of(
                new StoreMatchingEngine.StoreMatchResult(UUID.randomUUID().toString(), "Store A", 3, 1200.0),
                new StoreMatchingEngine.StoreMatchResult(UUID.randomUUID().toString(), "Store B", 2, 1800.0)
        );
        when(storeMatchingEngine.findOptimalStores(47.15, 27.58, 5000, request.getItemIds())).thenReturn(matches);

        ResponseEntity<List<StoreMatchingEngine.StoreMatchResult>> response = controller.lookupStores(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Store A", response.getBody().getFirst().storeName());
    }

    @Test
    void lookupStores_shouldReturn200AndEmptyListWhenNoStoresMatch() {
        StoreMatchRequest request = new StoreMatchRequest();
        request.setUserLat(47.15);
        request.setUserLng(27.58);
        request.setRadiusInMeters(5000);
        request.setItemIds(List.of(UUID.randomUUID()));

        when(storeMatchingEngine.findOptimalStores(47.15, 27.58, 5000, request.getItemIds())).thenReturn(List.of());

        ResponseEntity<List<StoreMatchingEngine.StoreMatchResult>> response = controller.lookupStores(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFullRoute_shouldReturn202WhenRouteNotYetInRedisButPending() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        when(redis.hasKey(startsWith("route:pending:"))).thenReturn(true);

        ResponseEntity<RoutingResponse> response = controller.getFullRoute("some-route-id");

        assertEquals(202, response.getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFullRoute_shouldReturn404WhenRouteNotYetInRedisAndNotPending() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        when(redis.hasKey(startsWith("route:pending:"))).thenReturn(false);

        ResponseEntity<RoutingResponse> response = controller.getFullRoute("some-route-id");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFullRoute_shouldReturn200WithRouteWhenPresentInRedis() throws Exception {
        String routeId = "test-route-id";

        RoutingResponse fullRoute = new RoutingResponse();
        fullRoute.setStatus("success");
        fullRoute.setRouteId(routeId);
        fullRoute.setRoute(List.of(
                new RoutePoint("user_loc", "Tu", 47.15, 27.58),
                new RoutePoint("item_1", "Lapte", 47.16, 27.59)
        ));
        fullRoute.setWarnings(List.of());
        fullRoute.setPartial(false);

        String json = objectMapper.writeValueAsString(fullRoute);

        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("route:" + routeId)).thenReturn(json);

        ResponseEntity<RoutingResponse> response = controller.getFullRoute(routeId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isPartial());
    }

    @Test
    void getMacroEstimates_shouldReturn200WithEstimates() {
        MacroRoutingResponse macroResponse = new MacroRoutingResponse(
                new MacroRoutingResponse.TransportEstimate(850.0, 612.0, null),
                new MacroRoutingResponse.TransportEstimate(1200.0, 145.0, null)

        );
        when(macroRoutingService.getEstimates(47.15, 27.58, "store-uuid")).thenReturn(macroResponse);

        ResponseEntity<MacroRoutingResponse> response = controller.getMacroEstimates(47.15, 27.58, "store-uuid");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getWalking());
        assertNotNull(response.getBody().getDriving());
        assertEquals(850.0, response.getBody().getWalking().getDistanceM(), 0.001);
    }

    @Test
    void getMacroEstimates_shouldReturn404WhenStoreNotFound() {
        when(macroRoutingService.getEstimates(anyDouble(), anyDouble(), anyString())).thenReturn(null);

        ResponseEntity<MacroRoutingResponse> response = controller.getMacroEstimates(47.15, 27.58, "nonexistent");

        assertEquals(404, response.getStatusCode().value());
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
        assertTrue(response.getBody().isLowConfidenceWarning());
        verify(locationProcessorWorker).recalculateSingleItem(storeId, itemId);
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

        verify(locationProcessorWorker, times(1)).recalculateSingleItem(storeId, itemId);
    }
}