package com.p2ps.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class StoreMatchingEngine {

    private static final Logger logger = LoggerFactory.getLogger(StoreMatchingEngine.class);


    private static final double METERS_PER_DEGREE = 111320.0;

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public StoreMatchingEngine(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public List<StoreMatchResult> findOptimalStores(double userLat,
                                                    double userLng,
                                                    double radiusInMeters,
                                                    List<UUID> itemIds,
                                                    List<String> itemNames) {
        Set<UUID> routableItemIds = new LinkedHashSet<>();
        if (itemIds != null) {
            routableItemIds.addAll(itemIds);
        }
        routableItemIds.addAll(resolveRoutableIdsByName(itemNames));

        return findOptimalStores(userLat, userLng, radiusInMeters, new ArrayList<>(routableItemIds));
    }

    public List<StoreMatchResult> findOptimalStores(double userLat, double userLng, double radiusInMeters, List<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            logger.warn("Lista de produse este goala. Nu se poate calcula magazinul optim.");
            return List.of();
        }


        // Use a conservative over-approximation for the geometry bounding box.
        // At the equator, 1 degree longitude is ~111km. At higher latitudes, it's smaller.
        // We divide by cos(lat) to get a safe degree radius for ST_DWithin(geometry).
        double latRadians = Math.toRadians(userLat);
        double cosLat = Math.cos(latRadians);
        // Add a 2% safety margin and ensure we don't divide by zero at poles (though unlikely for stores)
        double safetyMargin = 1.02;
        double radiusInDegrees = (radiusInMeters / METERS_PER_DEGREE) * (1.0 / Math.max(cosLat, 0.01)) * safetyMargin;


        String sql = """
            WITH requested_items AS (
                SELECT id, name, catalog_id, external_item_id
                FROM items
                WHERE id IN (:itemIds)
            ),
            matched_inventory AS (
                -- 1. Produse rafinate deja (din store_inventory_map)
                SELECT
                    sim.store_id,
                    COALESCE(ri.id, sim.item_id) AS requested_item_id,
                    located_item.catalog_id AS matched_catalog_id
                FROM store_inventory_map sim
                JOIN items located_item ON located_item.id = sim.item_id
                LEFT JOIN requested_items ri
                  ON located_item.id = ri.id
                  OR (
                      ri.catalog_id IS NOT NULL
                      AND located_item.catalog_id = ri.catalog_id
                  )
                  OR (
                      ri.external_item_id IS NOT NULL
                      AND ri.external_item_id <> ''
                      AND located_item.external_item_id = ri.external_item_id
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
                            OR f_unaccent(LOWER(catalog.generic_name)) LIKE f_unaccent(LOWER(CONCAT('%', ri.name, '%')))
                            OR f_unaccent(LOWER(catalog.specific_name)) LIKE f_unaccent(LOWER(CONCAT('%', ri.name, '%')))
                        )
                  )
                WHERE sim.confidence_score >= 0
                  AND (
                      ri.id IS NOT NULL
                      OR located_item.catalog_id IN (:itemIds)
                      OR sim.item_id IN (:itemIds)
                  )

                UNION

                -- 2. Produse care au doar telemetrie brută (raw_user_pings) - FALLBACK
                SELECT
                    rup.store_id,
                    COALESCE(ri.id, rup.item_id) AS requested_item_id,
                    located_item.catalog_id AS matched_catalog_id
                FROM raw_user_pings rup
                JOIN items located_item ON located_item.id = rup.item_id
                LEFT JOIN requested_items ri
                  ON located_item.id = ri.id
                  OR (
                      ri.catalog_id IS NOT NULL
                      AND located_item.catalog_id = ri.catalog_id
                  )
                  OR (
                      ri.external_item_id IS NOT NULL
                      AND ri.external_item_id <> ''
                      AND located_item.external_item_id = ri.external_item_id
                  )
                  OR f_unaccent(LOWER(located_item.name)) = f_unaccent(LOWER(ri.name))
                  OR f_unaccent(LOWER(COALESCE(located_item.external_item_id, ''))) = f_unaccent(LOWER(ri.name))
                WHERE rup.accuracy_m < 30.0
                  AND (
                      ri.id IS NOT NULL
                      OR located_item.catalog_id IN (:itemIds)
                      OR rup.item_id IN (:itemIds)
                  )
            ),
            matched_with_prices AS (
                SELECT
                    mi.store_id,
                    mi.requested_item_id,
                    MIN(sp.price) AS matched_price
                FROM matched_inventory mi
                LEFT JOIN store_prices sp
                    ON sp.store_id = mi.store_id
                   AND sp.catalog_id = mi.matched_catalog_id
                GROUP BY mi.store_id, mi.requested_item_id
            ),
            store_price_aggregate AS (
                SELECT
                    store_id,
                    SUM(matched_price) AS total_estimated_price,
                    COUNT(*) FILTER (WHERE matched_price IS NOT NULL) AS priced_items
                FROM matched_with_prices
                GROUP BY store_id
            )
            SELECT
                sg.store_id::text AS store_id,
                sg.name,
                ST_Distance(sg.boundary_polygon::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_m,
                COUNT(DISTINCT mi.requested_item_id) AS matched_items,
                spa.total_estimated_price AS total_estimated_price,
                COALESCE(spa.priced_items, 0) AS priced_items
            FROM store_geofences sg
            LEFT JOIN matched_inventory mi
                ON sg.store_id = mi.store_id
            LEFT JOIN store_price_aggregate spa
                ON sg.store_id = spa.store_id
            WHERE
                -- Pasul 1: Pre-filtrare rapidă folosind indexul pe geometrie
                ST_DWithin(sg.boundary_polygon, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), :radiusDegrees)
                -- Pasul 2: Filtrare exactă pe geografie (metrică)
                AND ST_DWithin(sg.boundary_polygon::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
            GROUP BY sg.store_id, sg.name, sg.boundary_polygon, spa.total_estimated_price, spa.priced_items
            HAVING COUNT(DISTINCT mi.requested_item_id) > 0
            ORDER BY
                matched_items DESC,
                COALESCE(spa.priced_items, 0) DESC,
                spa.total_estimated_price ASC NULLS LAST,
                distance_m
            LIMIT 3
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
                        rs.getDouble("distance_m"),
                        rs.getBigDecimal("total_estimated_price"),
                        rs.getInt("priced_items")
                )
        );

        if (results.isEmpty()) {
            logger.info("Nu a fost gasit niciun magazin in raza specificata care sa contina produsele dorite.");
            return List.of();
        }

        StoreMatchResult bestStore = results.getFirst();
        logger.info("Gasite {} magazine optime. Cel mai bun: {} (ID: {}) - Produse gasite: {}/{}, Distanta: {}m",
                results.size(), bestStore.storeName(), bestStore.storeId(), bestStore.matchedItems(), itemIds.size(), Math.round(bestStore.distanceMeters()));

        return results;
    }

    private List<UUID> resolveRoutableIdsByName(List<String> itemNames) {
        if (itemNames == null || itemNames.isEmpty()) {
            return List.of();
        }

        Set<UUID> resolvedIds = new LinkedHashSet<>();
        String sql = """
            SELECT id
            FROM (
                SELECT i.id, 0 AS rank
                FROM items i
                WHERE f_unaccent(LOWER(i.name)) = f_unaccent(LOWER(:itemName))
                   OR f_unaccent(LOWER(COALESCE(i.external_item_id, ''))) = f_unaccent(LOWER(:itemName))
                   OR f_unaccent(LOWER(i.name)) = f_unaccent(LOWER(CONCAT('Imported Item ', :itemName)))
                UNION
                SELECT p.id, 1 AS rank
                FROM p2p_product_catalog p
                WHERE f_unaccent(LOWER(p.generic_name)) = f_unaccent(LOWER(:itemName))
                   OR f_unaccent(LOWER(p.specific_name)) = f_unaccent(LOWER(:itemName))
                   OR f_unaccent(LOWER(p.generic_name)) LIKE f_unaccent(LOWER(CONCAT('%', :itemName, '%')))
                   OR f_unaccent(LOWER(p.specific_name)) LIKE f_unaccent(LOWER(CONCAT('%', :itemName, '%')))
            ) matches
            ORDER BY rank
            LIMIT 10
        """;

        for (String itemName : itemNames) {
            if (itemName == null || itemName.isBlank()) {
                continue;
            }
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("itemName", itemName.trim());
            resolvedIds.addAll(namedJdbcTemplate.queryForList(sql, parameters, UUID.class));
        }

        return new ArrayList<>(resolvedIds);
    }

    public record StoreMatchResult(
            String storeId,
            String storeName,
            int matchedItems,
            double distanceMeters,
            java.math.BigDecimal totalEstimatedPrice,
            int pricedItems
    ) {}
}
