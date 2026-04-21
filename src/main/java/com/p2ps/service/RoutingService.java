package com.p2ps.service;

import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import com.p2ps.controller.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoutingService {

    private static final Logger logger = LoggerFactory.getLogger(RoutingService.class);

    private static final double CONFIDENCE_THRESHOLD = 0.3;
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private final JdbcTemplate jdbcTemplate;

    public RoutingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RoutingResponse calculateOptimalRoute(RoutingRequest request) {
        logger.info("Calculez ruta pentru {} produse de la ({}, {})",
                request.getProductIds() == null ? 0 : request.getProductIds().size(),
                request.getUserLat(), request.getUserLng());

        List<String> warnings = new ArrayList<>();

        String storeId = findStoreForUser(request.getUserLat(), request.getUserLng());
        if (storeId == null) {
            logger.warn("Userul nu se afla in niciun magazin cunoscut.");
            return new RoutingResponse("error", List.of(), List.of("Nu esti in niciun magazin cunoscut."));
        }

        List<ProductLocation> locations = getProductLocations(request.getProductIds(), storeId, warnings);
        if (locations.isEmpty()) {
            return new RoutingResponse("error", List.of(), List.of("Niciunul din produsele cerute nu a fost gasit in magazin."));
        }

        RoutePoint userPoint = new RoutePoint("user_loc", "Tu", request.getUserLat(), request.getUserLng());
        List<RoutePoint> nnRoute = nearestNeighborTSP(userPoint, toRoutePoints(locations));
        nnRoute.add(0, userPoint);

        List<RoutePoint> optimizedRoute = threeOptImprove(nnRoute);

        double distanceBefore = routeDistance(nnRoute);
        double distanceAfter  = routeDistance(optimizedRoute);
        logger.info("NN: {}m | 3-Opt: {}m | Imbunatatire: {}%",
                (int) distanceBefore, (int) distanceAfter,
                String.format("%.1f", distanceBefore > 0 ? (distanceBefore - distanceAfter) / distanceBefore * 100 : 0));

        logger.info("Ruta calculata: {} puncte, {} warnings", optimizedRoute.size(), warnings.size());
        return new RoutingResponse("success", optimizedRoute, warnings);
    }

    private String findStoreForUser(double lat, double lng) {
        String sql = "SELECT store_id::text FROM store_geofences " +
                "WHERE ST_Contains(boundary_polygon, ST_SetSRID(ST_MakePoint(?, ?), 4326)) LIMIT 1";
        List<String> results = jdbcTemplate.queryForList(sql, String.class, lng, lat);
        return results.isEmpty() ? null : results.get(0);
    }

    private List<ProductLocation> getProductLocations(List<String> productIds, String storeId, List<String> warnings) {
        if (productIds == null || productIds.isEmpty()) return List.of();

        List<ProductLocation> locations = queryInventoryMap(productIds, storeId, warnings);

        if (locations.isEmpty()) {
            logger.info("store_inventory_map goala - fallback la raw_user_pings");
            warnings.add("Locatiile produselor sunt estimate din date brute.");
            locations = queryRawPingsCentroid(productIds, storeId);
        }

        return locations;
    }

    private List<ProductLocation> queryInventoryMap(List<String> productIds, String storeId, List<String> warnings) {
        String placeholders = productIds.stream().map(id -> "?").collect(Collectors.joining(", "));

        String sql = "SELECT sim.item_id::text AS item_id, i.name AS name, " +
                "ST_Y(sim.estimated_loc_point) AS lat, ST_X(sim.estimated_loc_point) AS lng, " +
                "sim.confidence_score " +
                "FROM store_inventory_map sim " +
                "JOIN items i ON sim.item_id = i.id " +
                "WHERE sim.item_id::text IN (" + placeholders + ") " +
                "AND sim.store_id::text = ?";

        List<Object> params = new ArrayList<>(productIds);
        params.add(storeId);

        List<ProductLocation> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProductLocation(
                        rs.getString("item_id"),
                        rs.getString("name"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getDouble("confidence_score")
                ),
                params.toArray(new Object[0])
        );

        for (ProductLocation loc : results) {
            if (loc.confidenceScore() < CONFIDENCE_THRESHOLD) {
                warnings.add(String.format(
                        "Locatia produsului '%s' are un grad de incredere scazut (%.0f%%).",
                        loc.name(), loc.confidenceScore() * 100
                ));
            }
        }

        for (String requestedId : productIds) {
            boolean found = results.stream().anyMatch(l -> l.itemId().equals(requestedId));
            if (!found) {
                warnings.add("Produsul cu ID '" + requestedId + "' nu a fost gasit in magazin.");
            }
        }

        return results;
    }

    private List<ProductLocation> queryRawPingsCentroid(List<String> productIds, String storeId) {
        logger.info(">>> Incepe queryRawPingsCentroid pentru {} produse", productIds.size());

        String placeholders = productIds.stream().map(id -> "?").collect(Collectors.joining(", "));

        String sql = "SELECT rup.item_id::text AS item_id, i.name AS name, " +
                "AVG(ST_Y(rup.location_point)) AS lat, AVG(ST_X(rup.location_point)) AS lng, " +
                "0.0 AS confidence_score " +
                "FROM raw_user_pings rup " +
                "JOIN items i ON rup.item_id = i.id " +
                "WHERE rup.item_id::text IN (" + placeholders + ") " +
                "AND rup.store_id::text = ? " +
                "AND rup.accuracy_m < 12.0 " +
                "GROUP BY rup.item_id, i.name";

        List<Object> params = new ArrayList<>(productIds);
        params.add(storeId);

        logger.info(">>> Execut query SQL");
        List<ProductLocation> result = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProductLocation(
                        rs.getString("item_id"),
                        rs.getString("name"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getDouble("confidence_score")
                ),
                params.toArray(new Object[0])
        );
        logger.info(">>> Query terminat, {} rezultate", result.size());
        return result;
    }

    double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    List<RoutePoint> nearestNeighborTSP(RoutePoint start, List<RoutePoint> points) {
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

    List<RoutePoint> threeOptImprove(List<RoutePoint> route) {
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
                    if (best != null) {
                        return best;
                    }
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
            default -> throw new IllegalArgumentException("variant trebuie sa fie intre 1 si 7");
        }
        return result;
    }

    private List<RoutePoint> reversed(List<RoutePoint> segment) {
        List<RoutePoint> copy = new ArrayList<>(segment);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private double edgeCost(List<RoutePoint> route, int from, int to) {
        if (to >= route.size()) return 0;
        RoutePoint a = route.get(from);
        RoutePoint b = route.get(to);
        return haversine(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }

    double routeDistance(List<RoutePoint> route) {
        double total = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            total += edgeCost(route, i, i + 1);
        }
        return total;
    }

    private List<RoutePoint> toRoutePoints(List<ProductLocation> locations) {
        return locations.stream()
                .map(l -> new RoutePoint(l.itemId(), l.name(), l.lat(), l.lng()))
                .toList();
    }

    public record ProductLocation(String itemId, String name, double lat, double lng, double confidenceScore) {}
}