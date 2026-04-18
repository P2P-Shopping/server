package com.p2ps.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LocationProcessorWorker {

    private final JdbcTemplate jdbcTemplate;

    public LocationProcessorWorker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void processAndCalculateCenters() {
        System.out.println("⏳ [Worker] Începem recalcularea globală a centrelor...");
        try {
            int deletedRows = jdbcTemplate.update("DELETE FROM store_inventory_map");
            System.out.println("ℹ️ [Worker] Am eliminat " + deletedRows + " locații vechi.");

            String sql = """
                INSERT INTO store_inventory_map (store_id, item_id, estimated_loc_point, confidence_score, ping_count)
                WITH FilteredPings AS (
                    SELECT item_id, store_id, location_point, accuracy_m
                    FROM raw_user_pings
                    WHERE loc_provider IN ('WIFI_RTT', 'GPS') AND accuracy_m < 12.0
                ),
                ClusteredData AS (
                    SELECT 
                        item_id, store_id, location_point, accuracy_m,
                        ST_ClusterDBSCAN(ST_Transform(location_point, 3857), eps := 3.0, minpoints := 10) 
                        OVER (PARTITION BY store_id, item_id) AS cluster_id
                    FROM FilteredPings
                ),
                ClusterStats AS (
                    SELECT 
                        store_id, item_id, cluster_id,
                        ST_GeometricMedian(ST_Collect(location_point)) AS estimated_loc_point,
                        LEAST(1.0, (COUNT(item_id) / 50.0) * (1.0 / GREATEST(1.0, AVG(accuracy_m)))) AS confidence_score, 
                        COUNT(item_id) AS ping_count
                    FROM ClusteredData
                    WHERE cluster_id IS NOT NULL
                    GROUP BY store_id, item_id, cluster_id
                )
                SELECT DISTINCT ON (store_id, item_id)
                    store_id, item_id, estimated_loc_point, confidence_score, ping_count
                FROM ClusterStats
                ORDER BY store_id, item_id, ping_count DESC, confidence_score DESC, cluster_id ASC
            """;

            int insertedRows = jdbcTemplate.update(sql);
            System.out.println("✅ [Worker] Recalculare globală finalizată. " + insertedRows + " produse mapate.");

        } catch (Exception e) {
            System.err.println("❌ [Worker] Eroare critică la recalcularea globală: " + e.getMessage());
            throw e;
        }
    }

    @Async
    @Transactional
    public void recalculateSingleItem(UUID storeId, UUID itemId) {
        System.out.println("⚡ [Rapid-Recalc] Intervenție de urgență pentru produsul: " + itemId);
        try {
            String sql = """
                WITH ItemPings AS (
                    SELECT location_point, accuracy_m
                    FROM raw_user_pings
                    WHERE store_id = ? AND item_id = ? AND accuracy_m < 12.0
                ),
                Clustered AS (
                    SELECT location_point, accuracy_m,
                           ST_ClusterDBSCAN(ST_Transform(location_point, 3857), eps := 3.0, minpoints := 3) 
                           OVER () AS cluster_id
                    FROM ItemPings
                )
                UPDATE store_inventory_map
                SET estimated_loc_point = (
                        SELECT ST_GeometricMedian(ST_Collect(location_point)) 
                        FROM Clustered WHERE cluster_id = 0
                        LIMIT 1
                    ),
                    confidence_score = (
                        SELECT LEAST(1.0, (COUNT(*) / 50.0) * (1.0 / GREATEST(1.0, AVG(accuracy_m))))
                        FROM Clustered WHERE cluster_id = 0
                    ),
                    ping_count = (SELECT COUNT(*) FROM Clustered WHERE cluster_id = 0)
                WHERE store_id = ? AND item_id = ?
            """;

            jdbcTemplate.update(sql, storeId, itemId, storeId, itemId);
            System.out.println("🎯 [Rapid-Recalc] Poziția produsului " + itemId + " a fost actualizată.");
        } catch (Exception e) {
            System.err.println("❌ [Rapid-Recalc] Eroare la recalcularea rapidă: " + e.getMessage());
        }
    }
}