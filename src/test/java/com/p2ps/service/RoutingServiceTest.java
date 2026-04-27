package com.p2ps.service;

import com.p2ps.controller.RoutePoint;
import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RoutingAsyncService routingAsyncService;

    @Mock
    private StringRedisTemplate redis;

    private RouteOptimizer optimizer;
    private RoutingService service;

    private static final String STORE_ID = "8f3e1a2b-c4d5-6e7f-8a9b-0c1d2e3f4a5b";
    private static final String ITEM_1 = "11111111-a1b2-c3d4-e5f6-1234567890ab";
    private static final String ITEM_2 = "22222222-b2c3-d4e5-f6a7-2345678901bc";
    private static final String ITEM_3 = "33333333-c3d4-e5f6-a7b8-3456789012cd";

    @BeforeEach
    void setUp() {
        optimizer = new RouteOptimizer();
        service = new RoutingService(jdbcTemplate, optimizer, routingAsyncService, redis);
    }

    // -------------------------------------------------------------------------
    // Haversine tests (now testing RouteOptimizer directly)
    // -------------------------------------------------------------------------

    @Test
    void haversine_shouldReturnZeroForSameCoordinates() {
        assertEquals(0.0, optimizer.haversine(47.156, 27.587, 47.156, 27.587), 0.001);
    }

    @Test
    void haversine_shouldReturnPositiveDistanceForDifferentCoordinates() {
        assertTrue(optimizer.haversine(47.156, 27.587, 47.157, 27.588) > 0);
    }

    @Test
    void haversine_shouldBeSymmetric() {
        double d1 = optimizer.haversine(47.156, 27.587, 47.160, 27.590);
        double d2 = optimizer.haversine(47.160, 27.590, 47.156, 27.587);
        assertEquals(d1, d2, 0.001);
    }

    // -------------------------------------------------------------------------
    // Nearest Neighbor TSP tests
    // -------------------------------------------------------------------------

    @Test
    void nearestNeighborTSP_shouldReturnAllPoints() {
        RoutePoint start = new RoutePoint("user", "Tu", 47.156, 27.587);
        List<RoutePoint> points = List.of(
                new RoutePoint(ITEM_1, "A", 47.157, 27.588),
                new RoutePoint(ITEM_2, "B", 47.158, 27.589),
                new RoutePoint(ITEM_3, "C", 47.159, 27.590)
        );

        List<RoutePoint> route = optimizer.nearestNeighborTSP(start, points);

        assertEquals(3, route.size());
        assertTrue(route.stream().anyMatch(p -> p.getItemId().equals(ITEM_1)));
        assertTrue(route.stream().anyMatch(p -> p.getItemId().equals(ITEM_2)));
        assertTrue(route.stream().anyMatch(p -> p.getItemId().equals(ITEM_3)));
    }

    @Test
    void nearestNeighborTSP_shouldStartFromNearestToStart() {
        RoutePoint start = new RoutePoint("user", "Tu", 47.156, 27.587);
        RoutePoint near = new RoutePoint(ITEM_1, "Near", 47.1561, 27.5871);
        RoutePoint far = new RoutePoint(ITEM_2, "Far", 47.200, 27.650);

        List<RoutePoint> route = optimizer.nearestNeighborTSP(start, List.of(far, near));

        assertEquals(ITEM_1, route.get(0).getItemId());
    }

    @Test
    void nearestNeighborTSP_shouldReturnEmptyForEmptyInput() {
        RoutePoint start = new RoutePoint("user", "Tu", 47.156, 27.587);
        List<RoutePoint> route = optimizer.nearestNeighborTSP(start, List.of());
        assertTrue(route.isEmpty());
    }

    // -------------------------------------------------------------------------
    // 3-Opt tests
    // -------------------------------------------------------------------------

    @Test
    void threeOptImprove_shouldNotIncreaseRouteDistance() {
        List<RoutePoint> route = List.of(
                new RoutePoint("u", "Tu", 47.156, 27.587),
                new RoutePoint(ITEM_1, "A", 47.160, 27.595),
                new RoutePoint(ITEM_2, "B", 47.158, 27.591),
                new RoutePoint(ITEM_3, "C", 47.162, 27.600)
        );

        double before = optimizer.routeDistance(route);
        List<RoutePoint> improved = optimizer.threeOptImprove(route);
        double after = optimizer.routeDistance(improved);

        assertTrue(after <= before + 1e-9);
        assertEquals(route.size(), improved.size());
    }

    @Test
    void threeOptImprove_shouldReturnAllSamePoints() {
        List<RoutePoint> route = List.of(
                new RoutePoint("u", "Tu", 47.156, 27.587),
                new RoutePoint(ITEM_1, "A", 47.160, 27.595),
                new RoutePoint(ITEM_2, "B", 47.155, 27.580),
                new RoutePoint(ITEM_3, "C", 47.162, 27.600)
        );

        List<RoutePoint> improved = optimizer.threeOptImprove(route);

        assertEquals(route.size(), improved.size());
        for (RoutePoint original : route) {
            assertTrue(improved.stream().anyMatch(p -> p.getItemId().equals(original.getItemId())));
        }
    }

    // -------------------------------------------------------------------------
    // calculateOptimalRoute — eager path (lazyN=0)
    // -------------------------------------------------------------------------

    @Test
    void calculateOptimalRoute_shouldReturnErrorWhenUserNotInStore() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        RoutingRequest request = new RoutingRequest(47.156, 27.587, List.of(ITEM_1), 0);
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("error", response.getStatus());
        assertNotNull(response.getRoute());
        assertTrue(response.getRoute().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void calculateOptimalRoute_shouldReturnSuccessForValidRequest() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(STORE_ID));

        RoutingService.ProductLocation p1 = new RoutingService.ProductLocation(ITEM_1, "Produs 1", 47.1562, 27.5871, 0.9);
        RoutingService.ProductLocation p2 = new RoutingService.ProductLocation(ITEM_2, "Produs 2", 47.1558, 27.5865, 0.8);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(p1, p2));

        RoutingRequest request = new RoutingRequest(47.156, 27.587, List.of(ITEM_1, ITEM_2), 0);
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("success", response.getStatus());
        assertFalse(response.isPartial());
        assertNotNull(response.getRoute());
        assertFalse(response.getRoute().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void calculateOptimalRoute_lazy_shouldReturnPartialResponseWithRouteId() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(STORE_ID));

        List<RoutingService.ProductLocation> locations = List.of(
                new RoutingService.ProductLocation(ITEM_1, "P1", 47.1562, 27.5871, 0.9),
                new RoutingService.ProductLocation(ITEM_2, "P2", 47.1558, 27.5865, 0.8),
                new RoutingService.ProductLocation(ITEM_3, "P3", 47.1555, 27.5860, 0.7),
                new RoutingService.ProductLocation("item4", "P4", 47.1552, 27.5855, 0.9),
                new RoutingService.ProductLocation("item5", "P5", 47.1550, 27.5850, 0.8),
                new RoutingService.ProductLocation("item6", "P6", 47.1548, 27.5845, 0.7),
                new RoutingService.ProductLocation("item7", "P7", 47.1546, 27.5840, 0.9),
                new RoutingService.ProductLocation("item8", "P8", 47.1544, 27.5835, 0.8)
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(locations);
        
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        RoutingRequest request = new RoutingRequest(47.156, 27.587,
                List.of(ITEM_1, ITEM_2, ITEM_3, "item4", "item5", "item6", "item7", "item8"), 5);
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("partial", response.getStatus());
        assertTrue(response.isPartial());
        assertNotNull(response.getRouteId());
        assertEquals(6, response.getRoute().size());
    }

    // -------------------------------------------------------------------------
    // routeDistance tests
    // -------------------------------------------------------------------------

    @Test
    void routeDistance_shouldReturnZeroForSinglePoint() {
        List<RoutePoint> route = List.of(new RoutePoint("u", "Tu", 47.156, 27.587));
        assertEquals(0.0, optimizer.routeDistance(route), 0.001);
    }

    @Test
    void routeDistance_shouldReturnPositiveForMultiplePoints() {
        List<RoutePoint> route = List.of(
                new RoutePoint("u", "Tu", 47.156, 27.587),
                new RoutePoint(ITEM_1, "A", 47.160, 27.595)
        );
        assertTrue(optimizer.routeDistance(route) > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void calculateOptimalRoute_shouldAddLowConfidenceWarning() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(STORE_ID));

        RoutingService.ProductLocation p1 = new RoutingService.ProductLocation(ITEM_1, "Produs 1", 47.1562, 27.5871, 0.0);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(p1));

        RoutingRequest request = new RoutingRequest(47.156, 27.587, List.of(ITEM_1), 0);
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("success", response.getStatus());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.getWarnings().stream().anyMatch(w -> w.contains("incredere scazut")));
    }
}
