package org.example;

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

    // Alege CÂND vrei să ruleze.
    // Exemplul acesta rulează la fiecare 5 minute (300.000 milisecunde).
    // Alternativă: @Scheduled(cron = "0 0 * * * *") pentru "o dată pe oră la fix".
    @Scheduled(fixedRate = 300000)
    @Transactional // Foarte important pentru a lega TRUNCATE de INSERT
    public void processAndCalculateCenters() {
        System.out.println("⏳ [Worker] Începem recalcularea centrelor absolute...");

        try {
            // 1. Golim tabela veche
            jdbcTemplate.execute("TRUNCATE store_inventory_map");

            // 2. Rulăm algoritmul DBSCAN + Geometric Median
            String sql = """
                INSERT INTO store_inventory_map (store_id, item_id, formatted_cluster_id, estimated_loc_point, confidence_score, ping_count)
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
                )
                SELECT 
                    c.store_id,
                    c.item_id,
                    (REPLACE(i.name, 'Produs ', '') || ' - ' || s.name) AS formatted_cluster_id,  
                    ST_GeometricMedian(ST_Collect(c.location_point)) AS estimated_loc_point,
                    LEAST(1.0, (COUNT(c.item_id) / 50.0) * (1.0 / GREATEST(1.0, AVG(c.accuracy_m)))) AS confidence_score, 
                    COUNT(c.item_id) AS ping_count
                FROM ClusteredData c
                JOIN store_geofences s ON c.store_id = s.store_id
                JOIN items i ON c.item_id = i.item_id
                WHERE c.cluster_id IS NOT NULL
                GROUP BY c.store_id, c.item_id, c.cluster_id, s.name, i.name
            """;

            // Executăm inserția
            int rowsAffected = jdbcTemplate.update(sql);

            System.out.println("✅ [Worker] Procesare finalizată cu succes! Au fost actualizate " + rowsAffected + " locații la raft.");

        } catch (Exception e) {
            System.err.println("❌ [Worker] Eroare la procesarea locațiilor: " + e.getMessage());
            // Aruncăm excepția mai departe pentru ca @Transactional să facă rollback automat
            throw e;
        }
    }
}