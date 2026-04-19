package com.p2ps.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LocationProcessorWorker {

    private static final Logger log = LoggerFactory.getLogger(LocationProcessorWorker.class);

    private final JdbcTemplate jdbcTemplate;

    public LocationProcessorWorker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void processAndCalculateCenters() {
        log.info("⏳ [Worker] Începem recalcularea globală a centrelor...");
        try {
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
                
                ON CONFLICT (store_id, item_id) 
                DO UPDATE SET 
                    estimated_loc_point = EXCLUDED.estimated_loc_point,
                    confidence_score = EXCLUDED.confidence_score,
                    ping_count = EXCLUDED.ping_count
            """;

            int affectedRows = jdbcTemplate.update(sql);
            log.info("✅ [Worker] Recalculare globală finalizată. {} produse procesate/actualizate.", affectedRows);

        } catch (Exception e) {
            log.error("❌ [Worker] Eroare critică la recalcularea globală", e);
            throw e;
        }
    }

    @Async
    @Transactional
    public void recalculateSingleItem(UUID storeId, UUID itemId) {
        log.info("⚡ [Rapid-Recalc] Intervenție de urgență pentru produsul: {}", itemId);
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
                ),
                ValidCluster AS (
                    SELECT 
                        ST_GeometricMedian(ST_Collect(location_point)) AS new_loc,
                        LEAST(1.0, (COUNT(*) / 50.0) * (1.0 / GREATEST(1.0, AVG(accuracy_m)))) AS new_conf,
                        COUNT(*) AS new_count
                    FROM Clustered 
                    WHERE cluster_id = 0
                    HAVING COUNT(*) > 0
                )
                UPDATE store_inventory_map sim
                SET estimated_loc_point = vc.new_loc,
                    confidence_score = vc.new_conf,
                    ping_count = vc.new_count
                FROM ValidCluster vc
                WHERE sim.store_id = ? AND sim.item_id = ?
            """;

            int updated = jdbcTemplate.update(sql, storeId, itemId, storeId, itemId);

            if (updated > 0) {
                log.info("🎯 [Rapid-Recalc] Poziția produsului {} a fost actualizată cu succes.", itemId);
            } else {
                log.warn("⚠️ [Rapid-Recalc] Produsul {} nu a putut fi recalculat (insuficiente pings valide sau zgomot prea mare).", itemId);
            }
        } catch (Exception e) {
            log.error("❌ [Rapid-Recalc] Eroare la recalcularea rapidă pentru produsul: {}", itemId, e);
            throw e;
        }
    }
}