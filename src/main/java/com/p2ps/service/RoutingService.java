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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoutingService {

    private static final Logger logger = LoggerFactory.getLogger(RoutingService.class);

    private static final double CONFIDENCE_THRESHOLD = 0.3;

    private final JdbcTemplate jdbcTemplate;
    private final RouteOptimizer optimizer;
    private final RoutingAsyncService asyncService;

    public RoutingService(JdbcTemplate jdbcTemplate,
                          RouteOptimizer optimizer,
                          RoutingAsyncService asyncService) {
        this.jdbcTemplate = jdbcTemplate;
        this.optimizer = optimizer;
        this.asyncService = asyncService;
    }

    // -------------------------------------------------------------------------
    // BE 3.1 — Lazy Routing entry point
    // -------------------------------------------------------------------------

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RoutingResponse calculateOptimalRoute(RoutingRequest request) {
        logger.info("Calculez ruta pentru {} produse de la ({}, {})",
                request.getProductIds() == null ? 0 : request.getProductIds().size(),
                request.getUserLat(), request.getUserLng());

        List<String> warnings = new ArrayList<>();

        String storeId = findStoreForUser(request.getUserLat(), request.getUserLng());
        if (storeId == null) {
            logger.warn("Userul nu se afla in niciun magazin cunoscut.");
            return RoutingResponse.error("Nu esti in niciun magazin cunoscut.");
        }

        List<ProductLocation> locations = getProductLocations(request.getProductIds(), storeId, warnings);
        if (locations.isEmpty()) {
            return RoutingResponse.error("Niciunul din produsele cerute nu a fost gasit in magazin.");
        }

        RoutePoint userPoint = new RoutePoint("user_loc", "Tu", request.getUserLat(), request.getUserLng());

        // Full NN route (fast — always computed eagerly)
        List<RoutePoint> nnRoute = new ArrayList<>();
        nnRoute.add(userPoint);
        nnRoute.addAll(optimizer.nearestNeighborTSP(userPoint, toRoutePoints(locations)));

        int lazyN = request.getLazyN();
        boolean goLazy = lazyN > 0 && nnRoute.size() > lazyN + 1; // +1 for user point

        if (goLazy) {
            return handleLazyRoute(nnRoute, lazyN, warnings);
        }

        // Eager path — same as before
        List<RoutePoint> optimizedRoute = optimizer.threeOptImprove(nnRoute);
        logImprovement(nnRoute, optimizedRoute);
        logger.info("Ruta calculata: {} puncte, {} warnings", optimizedRoute.size(), warnings.size());
        return RoutingResponse.eager(optimizedRoute, warnings);
    }

    /**
     * BE 3.1 — Lazy path.
     *
     * Returns the first lazyN stops immediately (NN order, no 3-opt yet).
     * Schedules full 3-opt optimization in the background.
     * The frontend polls GET /api/routing/full/{routeId} when it needs the rest.
     */
    private RoutingResponse handleLazyRoute(List<RoutePoint> fullNnRoute,
                                             int lazyN,
                                             List<String> warnings) {
        String routeId = UUID.randomUUID().toString();

        // Partial response: user point + first lazyN products
        List<RoutePoint> partial = fullNnRoute.subList(0, lazyN + 1); // inclusive of user point

        logger.info("Lazy routing: returnez {} noduri imediat, {} in background (routeId={})",
                partial.size(), fullNnRoute.size() - partial.size(), routeId);

        // Fire-and-forget: 3-opt on full route → Redis
        asyncService.completeRouteAsync(routeId, new ArrayList<>(fullNnRoute), new ArrayList<>(warnings));

        return RoutingResponse.partial(routeId, new ArrayList<>(partial), warnings);
    }

    // -------------------------------------------------------------------------
    // DB queries (unchanged from original)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void logImprovement(List<RoutePoint> before, List<RoutePoint> after) {
        if (!logger.isInfoEnabled()) return;
        double distBefore = optimizer.routeDistance(before);
        double distAfter  = optimizer.routeDistance(after);
        logger.info("NN: {}m | 3-Opt: {}m | Imbunatatire: {}%",
                (int) distBefore, (int) distAfter,
                String.format("%.1f", distBefore > 0
                        ? (distBefore - distAfter) / distBefore * 100 : 0));
    }

    private List<RoutePoint> toRoutePoints(List<ProductLocation> locations) {
        return locations.stream()
                .map(l -> new RoutePoint(l.itemId(), l.name(), l.lat(), l.lng()))
                .toList();
    }

    // Kept as thin wrappers so existing RoutingServiceTest still compiles without changes
    double haversine(double lat1, double lng1, double lat2, double lng2) {
        return optimizer.haversine(lat1, lng1, lat2, lng2);
    }

    List<RoutePoint> nearestNeighborTSP(RoutePoint start, List<RoutePoint> points) {
        return optimizer.nearestNeighborTSP(start, points);
    }

    List<RoutePoint> threeOptImprove(List<RoutePoint> route) {
        return optimizer.threeOptImprove(route);
    }

    double routeDistance(List<RoutePoint> route) {
        return optimizer.routeDistance(route);
    }

    public record ProductLocation(String itemId, String name, double lat, double lng, double confidenceScore) {}
}