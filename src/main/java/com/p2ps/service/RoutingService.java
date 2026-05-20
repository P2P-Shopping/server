package com.p2ps.service;

import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import com.p2ps.controller.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    // -------------------------------------------------------------------------
    // BE 3.1 — Lazy Routing entry point
    // -------------------------------------------------------------------------

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RoutingResponse calculateOptimalRoute(RoutingRequest request) {
        logger.info("Calculez ruta pentru {} produse de la ({}, {}), storeId={}",
                request.getProductIds() == null ? 0 : request.getProductIds().size(),
                request.getUserLat(), request.getUserLng(), request.getStoreId());

        List<String> warnings = new ArrayList<>();

        String storeId = request.getStoreId();
        if (storeId == null || storeId.isBlank()) {
            storeId = findStoreForUser(request.getUserLat(), request.getUserLng());
        }

        if (storeId == null) {
            logger.warn("Userul nu se afla in niciun magazin cunoscut si nici nu s-a trimis un storeId explicitly.");
            RoutingResponse errorResponse = new RoutingResponse();
            errorResponse.setStatus("error");
            errorResponse.setWarnings(List.of("Nu esti in niciun magazin cunoscut."));
            return errorResponse;
        }

        List<ProductLocation> locations = getProductLocations(request.getProductIds(), storeId, warnings);
        if (locations.isEmpty()) {
            RoutingResponse errorResponse = new RoutingResponse();
            errorResponse.setStatus("error");
            errorResponse.setWarnings(warnings.isEmpty()
                    ? List.of("Niciunul din produsele cerute nu a fost gasit in magazin.")
                    : warnings);
            return errorResponse;
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
        addAudioInstructions(optimizedRoute); // BE 3.2
        logger.info("Ruta calculata: {} puncte, {} warnings", optimizedRoute.size(), warnings.size());

        RoutingResponse response = new RoutingResponse();
        response.setStatus("success");
        response.setRoute(optimizedRoute);
        response.setWarnings(warnings);
        response.setPartial(false);

        // --- Injectarea metricilor pentru răspunsul complet (Eager) ---
        double distEager = optimizer.routeDistance(optimizedRoute);
        response.setTotalDistanceMeters(distEager);
        response.setTotalStops(optimizedRoute.size());
        response.setEstimatedTimeSeconds((int) (distEager / 1.4)); // viteză estimată 1.4 m/s

        return response;
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
        addAudioInstructions(partial); // BE 3.2

        logger.info("Lazy routing: returnez {} noduri imediat, {} in background (routeId={})",
                partial.size(), fullNnRoute.size() - partial.size(), routeId);

        // Set pending marker in Redis
        String pendingKey = RoutingAsyncService.PENDING_KEY_PREFIX + routeId;
        redis.opsForValue().set(pendingKey, "true", RoutingAsyncService.PENDING_TTL);

        // Fire-and-forget: 3-opt on full route → Redis
        asyncService.completeRouteAsync(routeId, new ArrayList<>(fullNnRoute), new ArrayList<>(warnings));

        RoutingResponse partialResponse = new RoutingResponse();
        partialResponse.setStatus("success"); // Fixed: Now correctly setting status to "partial"
        partialResponse.setRouteId(routeId);
        partialResponse.setRoute(new ArrayList<>(partial));
        partialResponse.setWarnings(warnings);
        partialResponse.setPartial(true);

        // --- Injectarea metricilor pentru ruta parțială returnată imediat (Lazy) ---
        double distLazy = optimizer.routeDistance(partial);
        partialResponse.setTotalDistanceMeters(distLazy);
        partialResponse.setTotalStops(partial.size());
        partialResponse.setEstimatedTimeSeconds((int) (distLazy / 1.4));

        return partialResponse;
    }

    // -------------------------------------------------------------------------
    // DB queries (unchanged from original)
    // -------------------------------------------------------------------------

    private String findStoreForUser(double lat, double lng) {
        String sql = "SELECT store_id::text FROM store_geofences " +
                "WHERE ST_Contains(boundary_polygon, ST_SetSRID(ST_MakePoint(?, ?), 4326)) LIMIT 1";
        List<String> results = jdbcTemplate.queryForList(sql, String.class, lng, lat);
        return results.isEmpty() ? null : results.getFirst();
    }

    private List<ProductLocation> getProductLocations(List<String> productIds, String storeId, List<String> warnings) {
        if (productIds == null || productIds.isEmpty()) return List.of();

        // 1. Încercăm să găsim locațiile rafinate în store_inventory_map
        List<ProductLocation> locations = new ArrayList<>(queryInventoryMap(productIds, storeId, warnings));

        // 2. Identificăm produsele care lipsesc din hartă
        Set<String> foundIdsAtFirstPass = locations.stream()
                .map(ProductLocation::itemId)
                .collect(java.util.stream.Collectors.toSet());

        List<String> missingFromMap = productIds.stream()
                .filter(id -> !foundIdsAtFirstPass.contains(id))
                .toList();

        // 3. Pentru produsele lipsă, facem fallback la centrul puncetelor brute (raw_user_pings)
        if (!missingFromMap.isEmpty()) {
            logger.info("Produsele {} lipsesc din store_inventory_map - fallback la raw_user_pings", missingFromMap);
            List<ProductLocation> fallbackLocations = queryRawPingsCentroid(missingFromMap, storeId);

            for (ProductLocation loc : fallbackLocations) {
                warnings.add(String.format("Produsul '%s' a fost găsit, dar locația sa este aproximativă.", loc.name()));
                locations.add(loc);
            }
        }

        // 4. Identificăm produsele care lipsesc COMPLET (nici în map, nici în pings) pentru a da warning final
        Set<String> finalFoundIds = locations.stream()
                .map(ProductLocation::itemId)
                .collect(java.util.stream.Collectors.toSet());

        List<String> totallyMissingIds = productIds.stream()
                .filter(id -> !finalFoundIds.contains(id))
                .toList();

        if (!totallyMissingIds.isEmpty()) {
            addMissingProductsWarnings(totallyMissingIds, warnings);
        }

        return locations;
    }

    private void addMissingProductsWarnings(List<String> missingIds, List<String> warnings) {
        String missingPlaceholders = String.join(", ", Collections.nCopies(missingIds.size(), "?"));
        String namesSql = String.format("SELECT id::text, name FROM items WHERE id::text IN (%s)", missingPlaceholders);

        Map<String, String> idToName = new HashMap<>();
        jdbcTemplate.queryForList(namesSql, missingIds.toArray())
                .forEach(row -> idToName.put((String) row.get("id"), (String) row.get("name")));

        for (String missingId : missingIds) {
            String itemName = idToName.getOrDefault(missingId, missingId);
            warnings.add("Nu am găsit '" + itemName + "' în acest magazin.");
        }
    }

    private List<ProductLocation> queryInventoryMap(List<String> productIds, String storeId, List<String> warnings) {
        if (productIds == null || productIds.isEmpty()) return List.of();

        String placeholders = String.join(", ", Collections.nCopies(productIds.size(), "?"));

        String sql = """
                WITH requested_items AS (
                    SELECT id, name, external_item_id, catalog_id
                    FROM items
                    WHERE id::text IN (%s)
                )
                SELECT DISTINCT ON (ri.id)
                    ri.id::text AS item_id,
                    ri.name AS name,
                    ST_Y(sim.estimated_loc_point) AS lat,
                    ST_X(sim.estimated_loc_point) AS lng,
                    sim.confidence_score
                FROM requested_items ri
                JOIN items located_item
                  ON located_item.id = ri.id
                  OR (
                      ri.external_item_id IS NOT NULL
                      AND located_item.external_item_id = ri.external_item_id
                  )
                  OR (
                      ri.catalog_id IS NOT NULL
                      AND located_item.catalog_id = ri.catalog_id
                  )
                  OR f_unaccent(LOWER(located_item.name)) = f_unaccent(LOWER(ri.name))
                  OR f_unaccent(LOWER(COALESCE(located_item.external_item_id, ''))) = f_unaccent(LOWER(ri.name))
                  OR EXISTS (
                      SELECT 1
                      FROM p2p_product_catalog catalog
                      WHERE catalog.id = located_item.catalog_id
                        AND (
                            f_unaccent(LOWER(catalog.generic_name)) = f_unaccent(LOWER(ri.name))
                            OR f_unaccent(LOWER(catalog.specific_name)) = f_unaccent(LOWER(ri.name))
                            OR f_unaccent(LOWER(catalog.generic_name)) LIKE f_unaccent(LOWER(CONCAT('%%', ri.name, '%%')))
                            OR f_unaccent(LOWER(catalog.specific_name)) LIKE f_unaccent(LOWER(CONCAT('%%', ri.name, '%%')))
                        )
                  )
                JOIN store_inventory_map sim ON sim.item_id = located_item.id
                WHERE sim.store_id::text = ?
                ORDER BY ri.id,
                         CASE WHEN located_item.id = ri.id THEN 0 ELSE 1 END,
                         sim.confidence_score DESC
                """.formatted(placeholders);

        List<Object> params = new ArrayList<>(productIds);
        params.add(storeId);

        List<ProductLocation> results = jdbcTemplate.query(
                sql,
                (rs, ignored) -> new ProductLocation(
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
                        "E posibil ca produsul '%s' sa nu mai existe in magazin (grad de incredere scazut: %.0f%%).",
                        loc.name(), loc.confidenceScore() * 100
                ));
            }
        }

        // Returnăm doar rezultatele găsite. Logic-ul de missing a fost mutat în getProductLocations.
        return results;
    }

    private List<ProductLocation> queryRawPingsCentroid(List<String> productIds, String storeId) {
        logger.info(">>> Incepe queryRawPingsCentroid pentru {} produse", productIds.size());

        String placeholders = String.join(", ", Collections.nCopies(productIds.size(), "?"));

        String sql = """
                WITH requested_items AS (
                    SELECT id, name, external_item_id, catalog_id
                    FROM items
                    WHERE id::text IN (%s)
                )
                SELECT
                    ri.id::text AS item_id,
                    ri.name AS name,
                    AVG(ST_Y(rup.location_point)) AS lat,
                    AVG(ST_X(rup.location_point)) AS lng,
                    0.1 AS confidence_score -- Mic grad de încredere pentru fallback
                FROM requested_items ri
                JOIN items located_item
                  ON located_item.id = ri.id
                  OR (
                      ri.external_item_id IS NOT NULL
                      AND located_item.external_item_id = ri.external_item_id
                  )
                  OR (
                      ri.catalog_id IS NOT NULL
                      AND located_item.catalog_id = ri.catalog_id
                  )
                  OR f_unaccent(LOWER(located_item.name)) = f_unaccent(LOWER(ri.name))
                  OR f_unaccent(LOWER(COALESCE(located_item.external_item_id, ''))) = f_unaccent(LOWER(ri.name))
                  OR EXISTS (
                      SELECT 1
                      FROM p2p_product_catalog catalog
                      WHERE catalog.id = located_item.catalog_id
                        AND (
                            f_unaccent(LOWER(catalog.generic_name)) = f_unaccent(LOWER(ri.name))
                            OR f_unaccent(LOWER(catalog.specific_name)) = f_unaccent(LOWER(ri.name))
                            OR f_unaccent(LOWER(catalog.generic_name)) LIKE f_unaccent(LOWER(CONCAT('%%', ri.name, '%%')))
                            OR f_unaccent(LOWER(catalog.specific_name)) LIKE f_unaccent(LOWER(CONCAT('%%', ri.name, '%%')))
                        )
                  )
                JOIN raw_user_pings rup ON rup.item_id = located_item.id
                WHERE rup.store_id::text = ?
                  AND rup.accuracy_m < 30.0
                GROUP BY ri.id, ri.name
                """.formatted(placeholders);

        List<Object> params = new ArrayList<>(productIds);
        params.add(storeId);

        logger.info(">>> Execut query SQL");
        List<ProductLocation> result = jdbcTemplate.query(
                sql,
                (rs, ignored) -> new ProductLocation(
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
    // BE 3.2 — Audio Instruction Generation
    // -------------------------------------------------------------------------

    void addAudioInstructions(List<RoutePoint> route) {
        if (route == null || route.size() < 2) {
            if (route != null && !route.isEmpty()) {
                route.getFirst().setAudioInstruction("Ai ajuns la destinație.");
            }
            return;
        }

        for (int i = 0; i < route.size() - 1; i++) {
            RoutePoint pCurrent = route.get(i);
            RoutePoint pNext = route.get(i + 1);
            double distance = optimizer.haversine(pCurrent.getLat(), pCurrent.getLng(), pNext.getLat(), pNext.getLng());

            String instruction;
            if (i < route.size() - 2) {
                RoutePoint pAfterNext = route.get(i + 2);
                String turn = calculateTurn(pCurrent, pNext, pAfterNext);
                instruction = String.format("În %d metri, %s pentru a găsi %s.",
                        (int) distance, turn, pAfterNext.getName());
            } else {
                instruction = String.format("În %d metri, vei ajunge la %s.",
                        (int) distance, pNext.getName());
            }
            pCurrent.setAudioInstruction(instruction);
        }

        route.getLast().setAudioInstruction("Ai ajuns la destinație.");
    }

    private String calculateTurn(RoutePoint p1, RoutePoint p2, RoutePoint p3) {
        double bearing1 = bearing(p1, p2);
        double bearing2 = bearing(p2, p3);
        double turnAngle = bearing2 - bearing1;

        // Normalize angle to [-180, 180]
        if (turnAngle > 180) turnAngle -= 360;
        if (turnAngle <= -180) turnAngle += 360;

        if (turnAngle > -45 && turnAngle <= 45) {
            return "mergi înainte";
        } else if (turnAngle > 45 && turnAngle <= 135) {
            return "ia-o la dreapta";
        } else if (turnAngle < -45 && turnAngle >= -135) {
            return "ia-o la stânga";
        } else {
            return "întoarce-te";
        }
    }

    private double bearing(RoutePoint p1, RoutePoint p2) {
        double lat1 = Math.toRadians(p1.getLat());
        double lon1 = Math.toRadians(p1.getLng());
        double lat2 = Math.toRadians(p2.getLat());
        double lon2 = Math.toRadians(p2.getLng());

        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
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

    public record ProductLocation(String itemId, String name, double lat, double lng, double confidenceScore) {}
}