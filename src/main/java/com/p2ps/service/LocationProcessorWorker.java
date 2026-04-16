package com.p2ps.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LocationProcessorWorker {

    private final JdbcTemplate jdbcTemplate;

    public LocationProcessorWorker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void processAndCalculateCenters() {
        System.out.println("⏳ [Worker] Începem recalcularea centrelor absolute...");

        try {
            // Ștergem datele vechi (cititorii văd snapshot-ul vechi datorită MVCC)
            int deletedRows = jdbcTemplate.update("DELETE FROM store_inventory_map");
            System.out.println("ℹ️ [Worker] Am șters " + deletedRows + " locații vechi.");

            // Rulăm algoritmul extrăgând DOAR clusterul principal pentru fiecare produs
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
                    -- Calculăm statisticile pentru TOATE clusterele găsite
                    SELECT 
                        store_id,
                        item_id,
                        cluster_id,
                        ST_GeometricMedian(ST_Collect(location_point)) AS estimated_loc_point,
                        LEAST(1.0, (COUNT(item_id) / 50.0) * (1.0 / GREATEST(1.0, AVG(accuracy_m)))) AS confidence_score, 
                        COUNT(item_id) AS ping_count
                    FROM ClusteredData
                    WHERE cluster_id IS NOT NULL
                    GROUP BY store_id, item_id, cluster_id
                )
                -- Selectăm UNIC pe produs/magazin, aducând în față clusterul cu cele mai multe puncte
                SELECT DISTINCT ON (store_id, item_id)
                    store_id,
                    item_id,
                    estimated_loc_point,
                    confidence_score,
                    ping_count
                FROM ClusterStats
                ORDER BY store_id, item_id, ping_count DESC
            """;

            int insertedRows = jdbcTemplate.update(sql);

            System.out.println("✅ [Worker] Tranzacție finalizată! Au fost actualizate atomic " + insertedRows + " locații unice la raft.");

        } catch (Exception e) {
            System.err.println("❌ [Worker] Eroare la procesarea locațiilor: " + e.getMessage());
            throw e;
        }
    }
}
