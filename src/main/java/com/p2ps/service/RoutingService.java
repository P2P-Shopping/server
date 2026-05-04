package com.p2ps.service;

import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import com.p2ps.controller.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redis;

    public RoutingService(JdbcTemplate jdbcTemplate,
            RouteOptimizer optimizer,
            RoutingAsyncService asyncService,
            StringRedisTemplate redis) {
        this.jdbcTemplate = jdbcTemplate;
        this.optimizer = optimizer;
        this.asyncService = asyncService;
        this.redis = redis;
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
            return RoutingResponse.error("Nu esti in niciun magazin cunoscut.");
        }

        List<ProductLocation> locations = getProductLocations(request.getProductIds(), storeId, warnings);
        if (locations.isEmpty()) {
            return RoutingResponse.error("Niciunul din produsele cerute nu a fost gasit in magazin.");
        }

        // Fetch the store's exit point (checkout counters).
        // If the store doesn't have one yet (column is NULL) we fall back
        // gracefully to the old open-path behaviour.
        RoutePoint checkoutPoint = fetchExitPoint(storeId);

        RoutePoint userPoint = new RoutePoint("user_loc", "Tu",
                request.getUserLat(), request.getUserLng(), "USER");

        // NN route: user → products (nearest-neighbour order)
        List<RoutePoint> nnRoute = new ArrayList<>();
        nnRoute.add(userPoint);
        nnRoute.addAll(optimizer.nearestNeighborTSP(userPoint, toRoutePoints(locations)));

        // Pin checkout as the fixed last node.
        // RouteOptimizer.threeOptImprove() never moves index 0 (user) or the
        // last index (checkout) — segA and segD are always preserved as-is.
        if (checkoutPoint != null) {
            nnRoute.add(checkoutPoint);
            logger.info("Ruta inchisa: {} → {} produse → Casa de marcat", "user_loc", locations.size());
        } else {
            logger.info("Ruta deschisa (magazinul nu are exit_point configurat).");
        }

        int lazyN = request.getLazyN();
        boolean goLazy = lazyN > 0 && nnRoute.size() > lazyN + 1;

        if (goLazy) {
            return handleLazyRoute(nnRoute, lazyN, warnings);
        }

        // Eager path — 3-opt on the full route (start and end pinned)
        List<RoutePoint> optimizedRoute = optimizer.threeOptImprove(nnRoute);
        logImprovement(nnRoute, optimizedRoute);
        logger.info("Ruta calculata: {} puncte, {} warnings", optimizedRoute.size(), warnings.size());

        RoutingResponse response = RoutingResponse.eager(optimizedRoute, warnings);

        // --- Injectarea metricilor pentru răspunsul complet (Eager) ---
        double distEager = optimizer.routeDistance(optimizedRoute);
        response.setTotalDistanceMeters(distEager);
        response.setTotalStops(optimizedRoute.size());
        response.setEstimatedTimeSeconds((int) (distEager / 1.4)); // viteză estimată 1.4 m/s
        response.setTotalDistanceMeters(optimizer.routeDistance(optimizedRoute));
        response.setTotalStops(optimizedRoute.size() - 1);
        // Assuming walking speed of ~1.4 m/s
        response.setEstimatedTimeSeconds((int) (response.getTotalDistanceMeters() / 1.4));

        return response;
    }

    /**
     * Returns the first lazyN stops immediately (NN order, no 3-opt yet).
     * Schedules full 3-opt optimization in the background.
     * The checkout point is always part of the full (background) route but is
     * intentionally excluded from the partial response — it will appear at the
     * end when the frontend polls GET /api/routing/full/{routeId}.
     */
    private RoutingResponse handleLazyRoute(List<RoutePoint> fullNnRoute,
                                            int lazyN,
                                            List<String> warnings) {
            int lazyN,
            List<String> warnings) {
        String routeId = UUID.randomUUID().toString();

        // Partial response: user point + first lazyN products
        List<RoutePoint> partial = fullNnRoute.subList(0, lazyN + 1);

        logger.info("Lazy routing: returnez {} noduri imediat, {} in background (routeId={})",
                partial.size(), fullNnRoute.size() - partial.size(), routeId);

        // Set pending marker in Redis
        String pendingKey = RoutingAsyncService.PENDING_KEY_PREFIX + routeId;
        redis.opsForValue().set(pendingKey, "true", RoutingAsyncService.PENDING_TTL);

        // Fire-and-forget: 3-opt on full route (checkout pinned at end) → Redis
        asyncService.completeRouteAsync(routeId, new ArrayList<>(fullNnRoute), new ArrayList<>(warnings));

        RoutingResponse partialResponse = RoutingResponse.partial(routeId, new ArrayList<>(partial), warnings);

        // --- Injectarea metricilor pentru ruta parțială returnată imediat (Lazy) ---
        double distLazy = optimizer.routeDistance(partial);
        partialResponse.setTotalDistanceMeters(distLazy);
        partialResponse.setTotalStops(partial.size());
        partialResponse.setEstimatedTimeSeconds((int) (distLazy / 1.4));

        return partialResponse;
        RoutingResponse response = RoutingResponse.partial(routeId, new ArrayList<>(partial), warnings);
        response.setTotalDistanceMeters(optimizer.routeDistance(partial));
        response.setTotalStops(partial.size() - 1);
        response.setEstimatedTimeSeconds((int) (response.getTotalDistanceMeters() / 1.4));

        return response;
    }

    // -------------------------------------------------------------------------
    // DB queries
    // -------------------------------------------------------------------------

    private String findStoreForUser(double lat, double lng) {
        String sql = "SELECT store_id::text FROM store_geofences " +
                "WHERE ST_Contains(boundary_polygon, ST_SetSRID(ST_MakePoint(?, ?), 4326)) LIMIT 1";
        List<String> results = jdbcTemplate.queryForList(sql, String.class, lng, lat);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Returns {@code null} when:
     * <ul>
     * <li>the store's {@code exit_point} column is NULL (not yet configured),
     * or</li>
     * <li>the store_id doesn't exist (shouldn't happen, but safe).</li>
     * </ul>
     * In both cases the caller falls back to the open-path behaviour.
     */
    private RoutePoint fetchExitPoint(String storeId) {
        String sql = "SELECT ST_Y(exit_point) AS lat, ST_X(exit_point) AS lng " +
                "FROM store_geofences " +
                "WHERE store_id::text = ? AND exit_point IS NOT NULL";
        try {
            return jdbcTemplate.queryForObject(sql,
                    (rs, rowNum) -> new RoutePoint(
                            "checkout",
                            "Casa de marcat",
                            rs.getDouble("lat"),
                            rs.getDouble("lng"),
                            "CHECKOUT"),
                    storeId);
        } catch (EmptyResultDataAccessException _) {
            logger.info("Magazinul {} nu are un exit_point configurat — ruta ramane deschisa.", storeId);
            return null;
        } catch (Exception e) {
            logger.warn("Nu am putut prelua exit_point pentru magazinul {}: {}", storeId, e.getMessage());
            return null;
        }
    }

    private List<ProductLocation> getProductLocations(List<String> productIds, String storeId, List<String> warnings) {
        if (productIds == null || productIds.isEmpty())
            return List.of();

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
                        rs.getDouble("confidence_score")),
                params.toArray(new Object[0]));

        for (ProductLocation loc : results) {
            if (loc.confidenceScore() < CONFIDENCE_THRESHOLD) {
                warnings.add(String.format(
                        "Locatia produsului '%s' are un grad de incredere scazut (%.0f%%).",
                        loc.name(), loc.confidenceScore() * 100));
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
                        rs.getDouble("confidence_score")),
                params.toArray(new Object[0]));
        logger.info(">>> Query terminat, {} rezultate", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void logImprovement(List<RoutePoint> before, List<RoutePoint> after) {
        if (!logger.isInfoEnabled())
            return;
        double distBefore = optimizer.routeDistance(before);
        double distAfter = optimizer.routeDistance(after);
        logger.info("NN: {}m | 3-Opt: {}m | Imbunatatire: {}%",
                (int) distBefore, (int) distAfter,
                String.format("%.1f", distBefore > 0
                        ? (distBefore - distAfter) / distBefore * 100
                        : 0));
    }

    private List<RoutePoint> toRoutePoints(List<ProductLocation> locations) {
        return locations.stream()
                .map(l -> new RoutePoint(l.itemId(), l.name(), l.lat(), l.lng()))
                .toList();
    }

    public record ProductLocation(String itemId, String name, double lat, double lng, double confidenceScore) {
    }
}
