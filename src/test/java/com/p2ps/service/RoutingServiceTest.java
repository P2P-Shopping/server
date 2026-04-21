package com.p2ps.service;

import com.p2ps.controller.RoutePoint;
import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RoutingService service;

    private static final String STORE_ID = "8f3e1a2b-c4d5-6e7f-8a9b-0c1d2e3f4a5b";
    private static final String ITEM_1 = "11111111-a1b2-c3d4-e5f6-1234567890ab";
    private static final String ITEM_2 = "22222222-b2c3-d4e5-f6a7-2345678901bc";
    private static final String ITEM_3 = "33333333-c3d4-e5f6-a7b8-3456789012cd";

    @BeforeEach
    void setUp() {
        service = new RoutingService(jdbcTemplate);
    }

    // -------------------------------------------------------------------------
    // Haversine tests
    // -------------------------------------------------------------------------

    @Test
    void haversine_shouldReturnZeroForSameCoordinates() {
        assertEquals(0.0, service.haversine(47.156, 27.587, 47.156, 27.587), 0.001);
    }

    @Test
    void haversine_shouldReturnPositiveDistanceForDifferentCoordinates() {
        assertTrue(service.haversine(47.156, 27.587, 47.157, 27.588) > 0);
    }

    @Test
    void haversine_shouldBeSymmetric() {
        double d1 = service.haversine(47.156, 27.587, 47.160, 27.590);
        double d2 = service.haversine(47.160, 27.590, 47.156, 27.587);
        assertEquals(d1, d2, 0.001);
    }

    // -------------------------------------------------------------------------
    // Nearest Neighbor TSP tests
    // -------------------------------------------------------------------------

    @Test
    void nearestNeighborTSP_shouldReturnAllPoints() {
        RoutePoint start = new RoutePoint("user", "Tu", 47.156, 27.587);
        List<RoutePoint> points = List.of(
                new RoutePoint("A", "Produs A", 47.157, 27.588),
                new RoutePoint("B", "Produs B", 47.158, 27.589),
                new RoutePoint("C", "Produs C", 47.155, 27.586)
        );

        List<RoutePoint> route = service.nearestNeighborTSP(start, points);

        assertEquals(3, route.size());
        assertTrue(route.stream().anyMatch(p -> p.getItemId().equals("A")));
        assertTrue(route.stream().anyMatch(p -> p.getItemId().equals("B")));
        assertTrue(route.stream().anyMatch(p -> p.getItemId().equals("C")));
    }

    @Test
    void nearestNeighborTSP_shouldStartWithNearestPoint() {
        RoutePoint start = new RoutePoint("user", "Tu", 47.156, 27.587);
        RoutePoint near = new RoutePoint("near", "Near", 47.1561, 27.5871);
        RoutePoint far = new RoutePoint("far", "Far", 47.200, 27.600);

        List<RoutePoint> route = service.nearestNeighborTSP(start, List.of(far, near));

        assertEquals("near", route.get(0).getItemId());
    }

    @Test
    void nearestNeighborTSP_shouldReturnEmptyForEmptyInput() {
        RoutePoint start = new RoutePoint("user", "Tu", 47.156, 27.587);
        List<RoutePoint> route = service.nearestNeighborTSP(start, List.of());
        assertTrue(route.isEmpty());
    }

    // -------------------------------------------------------------------------
    // 3-Opt tests
    // -------------------------------------------------------------------------

    @Test
    void threeOptImprove_shouldNotIncreaseTotalDistance() {
        List<RoutePoint> route = new ArrayList<>(List.of(
                new RoutePoint("user", "Tu", 47.156, 27.587),
                new RoutePoint("A", "Produs A", 47.158, 27.590),
                new RoutePoint("B", "Produs B", 47.155, 27.584),
                new RoutePoint("C", "Produs C", 47.160, 27.592)
        ));

        double before = service.routeDistance(route);
        List<RoutePoint> optimized = service.threeOptImprove(route);
        double after = service.routeDistance(optimized);

        assertTrue(after <= before + 0.001);
        assertEquals(route.size(), optimized.size());
    }

    @Test
    void threeOptImprove_shouldPreserveAllPoints() {
        List<RoutePoint> route = new ArrayList<>(List.of(
                new RoutePoint("user", "Tu", 47.156, 27.587),
                new RoutePoint("A", "A", 47.158, 27.590),
                new RoutePoint("B", "B", 47.155, 27.584),
                new RoutePoint("C", "C", 47.160, 27.592)
        ));

        List<RoutePoint> optimized = service.threeOptImprove(route);

        assertEquals(4, optimized.size());
        assertTrue(optimized.stream().anyMatch(p -> p.getItemId().equals("user")));
        assertTrue(optimized.stream().anyMatch(p -> p.getItemId().equals("A")));
        assertTrue(optimized.stream().anyMatch(p -> p.getItemId().equals("B")));
        assertTrue(optimized.stream().anyMatch(p -> p.getItemId().equals("C")));
    }

    @Test
    void routeDistance_shouldReturnZeroForSinglePoint() {
        List<RoutePoint> route = List.of(new RoutePoint("user", "Tu", 47.156, 27.587));
        assertEquals(0.0, service.routeDistance(route), 0.001);
    }

    @Test
    void routeDistance_shouldSumEdges() {
        List<RoutePoint> route = List.of(
                new RoutePoint("A", "A", 47.156, 27.587),
                new RoutePoint("B", "B", 47.157, 27.588),
                new RoutePoint("C", "C", 47.158, 27.589)
        );
        double total = service.routeDistance(route);
        double ab = service.haversine(47.156, 27.587, 47.157, 27.588);
        double bc = service.haversine(47.157, 27.588, 47.158, 27.589);
        assertEquals(ab + bc, total, 0.001);
    }

    // -------------------------------------------------------------------------
    // calculateOptimalRoute tests
    // -------------------------------------------------------------------------

    @Test
    void calculateOptimalRoute_shouldReturnErrorWhenUserNotInStore() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        RoutingRequest request = new RoutingRequest(0.0, 0.0, List.of(ITEM_1));
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("error", response.getStatus());
        assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    void calculateOptimalRoute_shouldReturnErrorWhenProductListEmpty() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(STORE_ID));

        RoutingRequest request = new RoutingRequest(47.156, 27.587, List.of());
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("error", response.getStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    void calculateOptimalRoute_shouldReturnSuccessFromInventoryMap() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(STORE_ID));

        RoutingService.ProductLocation p1 = new RoutingService.ProductLocation(ITEM_1, "Produs 1", 47.1562, 27.5871, 0.9);
        RoutingService.ProductLocation p2 = new RoutingService.ProductLocation(ITEM_2, "Produs 2", 47.1558, 27.5865, 0.8);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(p1, p2));

        RoutingRequest request = new RoutingRequest(47.156, 27.587, List.of(ITEM_1, ITEM_2));
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("success", response.getStatus());
        assertNotNull(response.getRoute());
        assertFalse(response.getRoute().isEmpty());
        assertEquals("user_loc", response.getRoute().get(0).getItemId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void calculateOptimalRoute_shouldFallbackToRawPingsWhenInventoryEmpty() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(STORE_ID));

        RoutingService.ProductLocation p1 = new RoutingService.ProductLocation(ITEM_1, "Produs 1", 47.1562, 27.5871, 0.0);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of())
                .thenReturn(List.of(p1));

        RoutingRequest request = new RoutingRequest(47.156, 27.587, List.of(ITEM_1));
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("success", response.getStatus());
        assertTrue(response.getWarnings().stream()
                .anyMatch(w -> w.contains("date brute")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void calculateOptimalRoute_shouldReturnErrorWhenNoProductsFound() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(STORE_ID));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of())
                .thenReturn(List.of());

        RoutingRequest request = new RoutingRequest(47.156, 27.587, List.of(ITEM_1));
        RoutingResponse response = service.calculateOptimalRoute(request);

        assertEquals("error", response.getStatus());
    }
}