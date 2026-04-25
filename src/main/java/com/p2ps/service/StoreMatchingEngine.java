package com.p2ps.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StoreMatchingEngine {

    private static final Logger logger = LoggerFactory.getLogger(StoreMatchingEngine.class);


    private static final double METERS_PER_DEGREE = 111320.0;

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public StoreMatchingEngine(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public StoreMatchResult findOptimalStore(double userLat, double userLng, double radiusInMeters, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            logger.warn("Lista de produse este goala. Nu se poate calcula magazinul optim.");
            return null;
        }


        double radiusInDegrees = radiusInMeters / METERS_PER_DEGREE;


        String sql = """
            SELECT
                sg.store_id::text AS store_id,
                sg.name,
                ST_Distance(sg.boundary_polygon::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_m,
                COUNT(sim.item_id) AS matched_items
            FROM store_geofences sg
            LEFT JOIN store_inventory_map sim
                ON sg.store_id = sim.store_id AND sim.item_id::text IN (:itemIds)
            WHERE 
                -- Pasul 1: Pre-filtrare rapidă folosind indexul pe geometrie
                ST_DWithin(sg.boundary_polygon, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), :radiusDegrees)
                -- Pasul 2: Filtrare exactă pe geografie (metrică)
                AND ST_DWithin(sg.boundary_polygon::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
            GROUP BY sg.store_id, sg.name, sg.boundary_polygon
            HAVING COUNT(sim.item_id) > 0
            ORDER BY matched_items DESC, distance_m ASC
            LIMIT 1
        """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("lng", userLng)
                .addValue("lat", userLat)
                .addValue("radiusMeters", radiusInMeters)
                .addValue("radiusDegrees", radiusInDegrees) // Trimitem raza in grade pt pasul 1
                .addValue("itemIds", itemIds);

        logger.info("Caut magazin optim pentru {} produse, la coordonatele ({}, {}) in raza de {} metri",
                itemIds.size(), userLat, userLng, radiusInMeters);

        List<StoreMatchResult> results = namedJdbcTemplate.query(
                sql,
                parameters,
                (rs, ignoredRowNum) -> new StoreMatchResult(
                        rs.getString("store_id"),
                        rs.getString("name"),
                        rs.getInt("matched_items"),
                        rs.getDouble("distance_m")
                )
        );

        if (results.isEmpty()) {
            logger.info("Nu a fost gasit niciun magazin in raza specificata care sa contina produsele dorite.");
            return null;
        }

        StoreMatchResult bestStore = results.getFirst();
        logger.info("Gasit magazin optim: {} (ID: {}) - Produse gasite: {}/{}, Distanta: {}m",
                bestStore.storeName(), bestStore.storeId(), bestStore.matchedItems(), itemIds.size(), Math.round(bestStore.distanceMeters()));

        return bestStore;
    }

    public record StoreMatchResult(String storeId, String storeName, int matchedItems, double distanceMeters) {}
}