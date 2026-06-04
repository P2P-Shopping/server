package com.p2ps.service;

import com.p2ps.client.OsrmClient;
import com.p2ps.controller.MacroRoutingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * BE 3.2 — Macro-Routing (Walking vs Driving).
 *
 * Calculates the estimated distance, transit time, and road geometry from the user's
 * current location to the selected store's entrance.
 * Returns separate estimates for foot and car, each including an Encoded Polyline
 * for the frontend to draw the actual route on a map.
 *
 * Store entrance = ST_Centroid(boundary_polygon) from store_geofences.
 */
@Service
public class MacroRoutingService {

    private static final Logger logger = LoggerFactory.getLogger(MacroRoutingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final OsrmClient osrmClient;

    public MacroRoutingService(JdbcTemplate jdbcTemplate, OsrmClient osrmClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.osrmClient = osrmClient;
    }

    /**
     * Returns walking and driving estimates from (userLat, userLng) to the store entrance.
     * Each estimate includes distanceM, durationSeconds, and a polyline for map rendering.
     *
     * @param storeId UUID string — must match a store_geofences.store_id
     * @return MacroRoutingResponse with walking and driving fields (either can be null if OSRM fails)
     */
    @Cacheable(value = "macroRouting", key = "T(String).format(\"%s:%.3f:%.3f\", #storeId, #userLat, #userLng)")
    public MacroRoutingResponse getEstimates(double userLat, double userLng, String storeId) {
        return getEstimates(userLat, userLng, storeId, null, null);
    }

    public MacroRoutingResponse getEstimates(double userLat, double userLng, String storeId,
                                              Double storeLat, Double storeLng) {
        double[] entrance = null;

        if (storeLat != null && storeLng != null) {
            entrance = new double[]{storeLat, storeLng};
        } else if (storeId != null) {
            double[] dbEntrance = fetchStoreEntrance(storeId);
            if (dbEntrance.length > 0) entrance = dbEntrance;
        }

        if (entrance == null) {
            logger.warn("Store not found and no coordinates provided for storeId={}", storeId);
            return null;
        }

        double storeLat2 = entrance[0];
        double storeLng2 = entrance[1];
        logger.info("Macro-routing: calculating estimates to store entrance");

        CompletableFuture<OsrmClient.TransportEstimate> walkingFuture = CompletableFuture.supplyAsync(
                () -> osrmClient.getEstimate(userLat, userLng, storeLat2, storeLng2, "walking"));
        CompletableFuture<OsrmClient.TransportEstimate> drivingFuture = CompletableFuture.supplyAsync(
                () -> osrmClient.getEstimate(userLat, userLng, storeLat2, storeLng2, "driving"));

        CompletableFuture.allOf(walkingFuture, drivingFuture).join();

        OsrmClient.TransportEstimate drivingRaw = drivingFuture.join();
        OsrmClient.TransportEstimate walkingRaw  = walkingFuture.join();

        // If OSRM has no walking profile (public demo only serves driving), the walking call
        // either returns null or the same duration as driving.  Fall back to a distance-based
        // estimate: average walking speed = 1.39 m/s (5 km/h), same polyline as driving.
        if (walkingRaw == null || isSameProfileResult(walkingRaw, drivingRaw)) {
            walkingRaw = estimateWalkingFromDriving(drivingRaw);
            logger.info("Walking OSRM unavailable or identical to driving — using distance-based estimate");
        }

        MacroRoutingResponse.TransportEstimate walking = toDto(walkingRaw);
        MacroRoutingResponse.TransportEstimate driving = toDto(drivingRaw);

        logger.info("Macro-routing result: walking={} driving={}", walking, driving);
        return new MacroRoutingResponse(walking, driving);
    }

    private double[] fetchStoreEntrance(String storeId) {
        String sql = "SELECT ST_Y(ST_Centroid(boundary_polygon)) AS lat, " +
                     "ST_X(ST_Centroid(boundary_polygon)) AS lng " +
                     "FROM store_geofences WHERE store_id = ? LIMIT 1";

        UUID uuid;
        try {
            uuid = UUID.fromString(storeId);
        } catch (IllegalArgumentException _) {
            logger.warn("Invalid storeId format");
            return new double[0];
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, uuid);
        if (rows.isEmpty()) return new double[0];

        Object latObj = rows.get(0).get("lat");
        Object lngObj = rows.get(0).get("lng");

        if (!(latObj instanceof Number) || !(lngObj instanceof Number)) {
            logger.warn("Centroid extraction returned null or non-number");
            return new double[0];
        }

        return new double[]{((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue()};
    }

    /** True when walking OSRM returned the same duration as driving (same profile was used). */
    private boolean isSameProfileResult(OsrmClient.TransportEstimate walking,
                                        OsrmClient.TransportEstimate driving) {
        if (driving == null) return false;
        return Math.abs(walking.durationSeconds() - driving.durationSeconds()) < 1.0;
    }

    /**
     * Estimates walking time from a driving estimate.
     * Uses average walking speed of 5 km/h (1.39 m/s) and reuses the driving polyline
     * so the map still draws the road-network route.
     */
    private OsrmClient.TransportEstimate estimateWalkingFromDriving(OsrmClient.TransportEstimate driving) {
        if (driving == null) return null;
        double walkingDuration = driving.distanceM() / 1.39;
        return new OsrmClient.TransportEstimate(driving.distanceM(), walkingDuration, driving.polyline());
    }

    private MacroRoutingResponse.TransportEstimate toDto(OsrmClient.TransportEstimate raw) {
        if (raw == null) return null;
        return new MacroRoutingResponse.TransportEstimate(raw.distanceM(), raw.durationSeconds(), raw.polyline());
    }

    /**
     * Gets the boundary_polygon for a store as GeoJSON.
     */
    public String getStorePolygonGeoJson(String storeId) {
        String sql = "SELECT ST_AsGeoJSON(boundary_polygon) FROM store_geofences WHERE store_id = ?::uuid";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, storeId);
        } catch (org.springframework.dao.EmptyResultDataAccessException _) {
            return null;
        }
    }
}
