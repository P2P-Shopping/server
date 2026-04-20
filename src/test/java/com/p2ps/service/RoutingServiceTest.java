package com.p2ps.service;

import com.p2ps.controller.RoutePoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void haversine_shouldReturnZeroForSameCoordinates() {
        RoutingService service = new RoutingService(jdbcTemplate);
        double dist = service.haversine(47.156, 27.587, 47.156, 27.587);
        assertEquals(0.0, dist, 0.001);
    }

    @Test
    void haversine_shouldReturnPositiveDistanceForDifferentCoordinates() {
        RoutingService service = new RoutingService(jdbcTemplate);
        double dist = service.haversine(47.156, 27.587, 47.157, 27.588);
        assertTrue(dist > 0);
    }

    @Test
    void nearestNeighborTSP_shouldReturnAllPoints() {
        RoutingService service = new RoutingService(jdbcTemplate);
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
    void threeOptImprove_shouldNotIncreaseTotalDistance() {
        RoutingService service = new RoutingService(jdbcTemplate);
        List<RoutePoint> route = List.of(
                new RoutePoint("user", "Tu", 47.156, 27.587),
                new RoutePoint("A", "Produs A", 47.158, 27.590),
                new RoutePoint("B", "Produs B", 47.155, 27.584),
                new RoutePoint("C", "Produs C", 47.160, 27.592)
        );

        double before = service.routeDistance(route);
        List<RoutePoint> optimized = service.threeOptImprove(new ArrayList<>(route));
        double after = service.routeDistance(optimized);

        assertTrue(after <= before + 0.001);
        assertEquals(route.size(), optimized.size());
    }
}
