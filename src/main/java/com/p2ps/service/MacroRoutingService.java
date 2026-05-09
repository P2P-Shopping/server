package com.p2ps.service;

import com.p2ps.client.OsrmClient;
import com.p2ps.controller.MacroRoutingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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
    public MacroRoutingResponse getEstimates(double userLat, double userLng, String storeId) {
        double[] entrance = fetchStoreEntrance(storeId);
        if (entrance.length == 0) {
            logger.warn("Store not found or has no boundary polygon");
            return null;
        }

        double storeLat = entrance[0];
        double storeLng = entrance[1];
        logger.info("Macro-routing: calculating estimates to store entrance");

        CompletableFuture<OsrmClient.TransportEstimate> walkingFuture = CompletableFuture.supplyAsync(
                () -> osrmClient.getEstimate(userLat, userLng, storeLat, storeLng, "foot"));
        CompletableFuture<OsrmClient.TransportEstimate> drivingFuture = CompletableFuture.supplyAsync(
                () -> osrmClient.getEstimate(userLat, userLng, storeLat, storeLng, "car"));

        CompletableFuture.allOf(walkingFuture, drivingFuture).join();

        MacroRoutingResponse.TransportEstimate walking = toDto(walkingFuture.join());
        MacroRoutingResponse.TransportEstimate driving = toDto(drivingFuture.join());

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
        } catch (IllegalArgumentException e) {
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

    private MacroRoutingResponse.TransportEstimate toDto(OsrmClient.TransportEstimate raw) {
        if (raw == null) return null;
        return new MacroRoutingResponse.TransportEstimate(raw.distanceM(), raw.durationSeconds(), raw.polyline());
    }
}
