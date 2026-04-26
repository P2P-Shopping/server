package com.p2ps.service;

import com.p2ps.controller.RoutePoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stateless component containing all TSP math.
 * Injected by both RoutingService (eager path) and RoutingAsyncService (background path).
 * No DB access, no Spring state — pure algorithms.
 */
@Component
public class RouteOptimizer {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    public double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public List<RoutePoint> nearestNeighborTSP(RoutePoint start, List<RoutePoint> points) {
        List<RoutePoint> unvisited = new ArrayList<>(points);
        List<RoutePoint> route = new ArrayList<>();
        RoutePoint current = start;

        while (!unvisited.isEmpty()) {
            RoutePoint nearest = null;
            double minDist = Double.MAX_VALUE;
            for (RoutePoint candidate : unvisited) {
                double dist = haversine(current.getLat(), current.getLng(),
                        candidate.getLat(), candidate.getLng());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = candidate;
                }
            }
            route.add(nearest);
            unvisited.remove(nearest);
            current = nearest;
        }
        return route;
    }

    public List<RoutePoint> threeOptImprove(List<RoutePoint> route) {
        List<RoutePoint> current = new ArrayList<>(route);
        List<RoutePoint> improved = tryImproveOnce(current);
        while (improved != null) {
            current = improved;
            improved = tryImproveOnce(current);
        }
        return current;
    }

    private List<RoutePoint> tryImproveOnce(List<RoutePoint> route) {
        int n = route.size();
        double currentDist = routeDistance(route);
        for (int i = 0; i < n - 2; i++) {
            for (int j = i + 1; j < n - 1; j++) {
                for (int k = j + 1; k < n - 1; k++) {
                    List<RoutePoint> best = findBestReconnect(route, i, j, k, currentDist);
                    if (best != null) return best;
                }
            }
        }
        return null;
    }

    private List<RoutePoint> findBestReconnect(List<RoutePoint> route, int i, int j, int k, double currentDist) {
        List<RoutePoint> best = null;
        double bestDist = currentDist;
        for (int variant = 1; variant < 8; variant++) {
            List<RoutePoint> candidate = reconnect(route, i, j, k, variant);
            double candidateDist = routeDistance(candidate);
            if (candidateDist < bestDist - 1e-10) {
                bestDist = candidateDist;
                best = candidate;
            }
        }
        return best;
    }

    private List<RoutePoint> reconnect(List<RoutePoint> route, int i, int j, int k, int variant) {
        List<RoutePoint> segA = new ArrayList<>(route.subList(0, i + 1));
        List<RoutePoint> segB = new ArrayList<>(route.subList(i + 1, j + 1));
        List<RoutePoint> segC = new ArrayList<>(route.subList(j + 1, k + 1));
        List<RoutePoint> segD = new ArrayList<>(route.subList(k + 1, route.size()));
        List<RoutePoint> segBr = reversed(segB);
        List<RoutePoint> segCr = reversed(segC);

        List<RoutePoint> result = new ArrayList<>(segA);
        switch (variant) {
            case 1 -> { result.addAll(segBr); result.addAll(segC);  result.addAll(segD); }
            case 2 -> { result.addAll(segB);  result.addAll(segCr); result.addAll(segD); }
            case 3 -> { result.addAll(segBr); result.addAll(segCr); result.addAll(segD); }
            case 4 -> { result.addAll(segC);  result.addAll(segB);  result.addAll(segD); }
            case 5 -> { result.addAll(segC);  result.addAll(segBr); result.addAll(segD); }
            case 6 -> { result.addAll(segCr); result.addAll(segB);  result.addAll(segD); }
            case 7 -> { result.addAll(segCr); result.addAll(segBr); result.addAll(segD); }
            default -> throw new IllegalArgumentException("variant must be between 1 and 7");
        }
        return result;
    }

    private List<RoutePoint> reversed(List<RoutePoint> segment) {
        List<RoutePoint> copy = new ArrayList<>(segment);
        Collections.reverse(copy);
        return copy;
    }

    public double routeDistance(List<RoutePoint> route) {
        double total = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            RoutePoint a = route.get(i);
            RoutePoint b = route.get(i + 1);
            total += haversine(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        }
        return total;
    }
}
