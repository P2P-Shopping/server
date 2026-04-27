package com.p2ps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.controller.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoutingAsyncServiceTest {

    private RouteOptimizer optimizer;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RoutingAsyncService service;

    @BeforeEach
    void setUp() {
        optimizer = new RouteOptimizer(); // real — stateless math
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new RoutingAsyncService(optimizer, redis, new ObjectMapper());
    }

    @Test
    void completeRouteAsync_shouldSaveOptimizedRouteToRedis() {
        String routeId = "test-route-id";
        List<RoutePoint> route = List.of(
                new RoutePoint("user", "Tu", 47.156, 27.587),
                new RoutePoint("item1", "Lapte", 47.157, 27.588),
                new RoutePoint("item2", "Paine", 47.158, 27.589)
        );

        // completeRouteAsync is @Async but we call it directly in test — runs synchronously
        service.completeRouteAsync(routeId, route, List.of());

        verify(valueOps).set(
                eq(RoutingAsyncService.ROUTE_KEY_PREFIX + routeId),
                argThat(json -> json.contains("\"status\":\"success\"")),
                any()
        );
    }

    @Test
    void completeRouteAsync_shouldIncludeWarningsInSavedResponse() {
        String routeId = "test-route-warnings";
        List<RoutePoint> route = List.of(
                new RoutePoint("user", "Tu", 47.156, 27.587),
                new RoutePoint("item1", "Produs", 47.157, 27.588)
        );
        List<String> warnings = List.of("Locatia produsului X are grad de incredere scazut.");

        service.completeRouteAsync(routeId, route, warnings);

        verify(valueOps).set(
                eq(RoutingAsyncService.ROUTE_KEY_PREFIX + routeId),
                argThat(json -> json.contains("incredere")),
                any()
        );
    }

    @Test
    void completeRouteAsync_shouldNotThrowWhenRedisThrows() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));

        List<RoutePoint> route = List.of(
                new RoutePoint("user", "Tu", 47.156, 27.587)
        );

        // Should not propagate exception — catches internally
        assertDoesNotThrow(() ->
                service.completeRouteAsync("error-route", route, List.of())
        );
    }

    @Test
    void completeRouteAsync_shouldSaveRouteWithCorrectRouteId() {
        String routeId = "specific-route-id";
        List<RoutePoint> route = List.of(
                new RoutePoint("user", "Tu", 47.156, 27.587),
                new RoutePoint("item1", "A", 47.157, 27.588)
        );

        service.completeRouteAsync(routeId, route, List.of());

        verify(valueOps).set(
                eq("route:" + routeId),
                anyString(),
                any()
        );
    }

    @Test
    void routeKeyPrefix_shouldBeCorrect() {
        assertEquals("route:", RoutingAsyncService.ROUTE_KEY_PREFIX);
    }
}
