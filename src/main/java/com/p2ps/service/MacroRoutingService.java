package com.p2ps.service;

import com.p2ps.client.OsrmClient;
import com.p2ps.controller.MacroRoutingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * BE 3.2 — Macro-Routing (Walking vs Driving).
 *
 * Calculates the estimated distance and transit time from the user's current location
 * to the selected store's entrance. Returns separate estimates for foot and car.
 *
 * Store entrance = ST_Centroid(boundary_polygon) from store_geofences.
 * No new DB column or Flyway migration needed.
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
     *
     * @param storeId UUID string — must match a store_geofences.store_id
     * @return MacroRoutingResponse with walking and driving fields (either can be null if OSRM fails)
     */
    public MacroRoutingResponse getEstimates(double userLat, double userLng, String storeId) {
        double[] entrance = fetchStoreEntrance(storeId);
        if (entrance == null) {
            logger.warn("Store not found or has no boundary polygon: storeId={}", storeId);
            return null;
        }

        double storeLat = entrance[0];
        double storeLng = entrance[1];
        logger.info("Macro-routing: user=({},{}) → store entrance=({},{}) storeId={}",
                userLat, userLng, storeLat, storeLng, storeId);

        OsrmClient.TransportEstimate walkingRaw = osrmClient.getEstimate(userLat, userLng, storeLat, storeLng, "foot");
        OsrmClient.TransportEstimate drivingRaw = osrmClient.getEstimate(userLat, userLng, storeLat, storeLng, "car");

        MacroRoutingResponse.TransportEstimate walking = toDto(walkingRaw);
        MacroRoutingResponse.TransportEstimate driving = toDto(drivingRaw);

        logger.info("Macro-routing result: walking={} driving={}", walking, driving);
        return new MacroRoutingResponse(walking, driving);
    }

    /**
     * Uses ST_Centroid of the store's boundary_polygon as the entrance point.
     * Returns [lat, lng] or null if the store doesn't exist.
     */
    private double[] fetchStoreEntrance(String storeId) {
        String sql = "SELECT ST_Y(ST_Centroid(boundary_polygon)) AS lat, " +
                     "ST_X(ST_Centroid(boundary_polygon)) AS lng " +
                     "FROM store_geofences WHERE store_id::text = ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, storeId);
        if (rows.isEmpty()) return null;

        double lat = ((Number) rows.get(0).get("lat")).doubleValue();
        double lng = ((Number) rows.get(0).get("lng")).doubleValue();
        return new double[]{lat, lng};
    }

    private MacroRoutingResponse.TransportEstimate toDto(OsrmClient.TransportEstimate raw) {
        if (raw == null) return null;
        return new MacroRoutingResponse.TransportEstimate(raw.distanceM(), raw.durationSeconds());
    }
}
