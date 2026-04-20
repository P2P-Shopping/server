package com.p2ps.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
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

    /**
     * Sincronizare globală a centrelor produselor folosind o strategie non-destructivă.
     * [Fix] Metoda nu mai este @Scheduled pentru a preveni blocajele de context la startup.
     * [Fix] Folosește UPSERT (ON CONFLICT) în loc de DELETE pentru integritatea datelor.
     */
    @Transactional
    public void processAndCalculateCenters() {
        log.info("⏳ [Admin-Task] Începem sincronizarea globală a centrelor (Strategie UPSERT)...");

        try {
            // Calculăm centrele noi și le fuzionăm cu cele existente
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
                ),
                RankedClusters AS (
                    SELECT DISTINCT ON (store_id, item_id)
                        store_id, item_id, estimated_loc_point, confidence_score, ping_count
                    FROM ClusterStats
                    ORDER BY store_id, item_id, ping_count DESC, confidence_score DESC
                )
                SELECT store_id, item_id, estimated_loc_point, confidence_score, ping_count
                FROM RankedClusters
                ON CONFLICT (store_id, item_id) 
                DO UPDATE SET 
                    estimated_loc_point = EXCLUDED.estimated_loc_point,
                    confidence_score = EXCLUDED.confidence_score,
                    ping_count = EXCLUDED.ping_count;
            """;

            int affectedRows = jdbcTemplate.update(sql);
            log.info("✅ [Admin-Task] Sincronizare finalizată. {} rânduri actualizate/inserate.", affectedRows);

        } catch (Exception e) {
            log.error("❌ [Admin-Task] Eroare critică la sincronizarea globală: ", e);
            throw e;
        }
    }

    /**
     * Recalculare rapidă asincronă pentru un singur produs.
     * [Fix] Selectează cel mai bun cluster disponibil (nu doar ID 0).
     * [Fix] Harden: Necesită minim 5 puncte (HAVING COUNT >= 5) pentru validitate.
     */
    @Async("telemetryExecutor")
    @Transactional
    public void recalculateSingleItem(UUID storeId, UUID itemId) {
        log.info("⚡ [Rapid-Recalc] Analiză clustere pentru produsul: {}", itemId);
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
                BestCluster AS (
                    SELECT 
                        ST_GeometricMedian(ST_Collect(location_point)) AS new_loc,
                        LEAST(1.0, (COUNT(*) / 50.0) * (1.0 / GREATEST(1.0, AVG(accuracy_m)))) AS new_conf,
                        COUNT(*) AS new_count
                    FROM Clustered 
                    WHERE cluster_id IS NOT NULL
                    GROUP BY cluster_id
                    HAVING COUNT(*) >= 5  -- [Fix] Prag minim de densitate pentru a ignora zgomotul
                    ORDER BY new_count DESC, new_conf DESC
                    LIMIT 1
                )
                UPDATE store_inventory_map sim
                SET estimated_loc_point = bc.new_loc,
                    confidence_score = bc.new_conf,
                    ping_count = bc.new_count
                FROM BestCluster bc
                WHERE sim.store_id = ? AND sim.item_id = ?;
            """;

            // Executăm update-ul folosind parametrii de intrare (storeId și itemId repetați pentru JOIN-ul de la final)
            int updated = jdbcTemplate.update(sql, storeId, itemId, storeId, itemId);

            if (updated > 0) {
                log.info("🎯 [Rapid-Recalc] Poziție actualizată cu succes pentru: {}", itemId);
            } else {
                log.warn("⚠️ [Rapid-Recalc] Nu s-a găsit niciun cluster care să treacă pragul de siguranță (min 5 puncte) pentru: {}", itemId);
            }
        } catch (Exception e) {
            log.error("❌ [Rapid-Recalc] Eroare la recalcularea produsului " + itemId, e);
            throw e;
        }
    }
}
package com.p2ps.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

@Component
public class LocationProcessorWorker {

    private static final Logger logger = LoggerFactory.getLogger(LocationProcessorWorker.class);

    private final JdbcTemplate jdbcTemplate;

    private DataSource dataSource;

    @Autowired
    public LocationProcessorWorker(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void ensureInventoryMapSchema() {
        if (dataSource == null || !isPostgreSQL()) {
            return;
        }

        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS raw_user_pings (
                ping_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                store_id        UUID NOT NULL,
                item_id         UUID NOT NULL,
                location_point  GEOMETRY(Point, 4326) NOT NULL,
                accuracy_m      DOUBLE PRECISION,
                floor_level     INT DEFAULT 0,
                loc_provider    VARCHAR(50),
                marked_at       TIMESTAMP DEFAULT NOW()
            )
        """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS store_inventory_map (
                map_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                store_id            UUID NOT NULL,
                item_id             UUID NOT NULL,
                estimated_loc_point GEOMETRY(Point, 4326) NOT NULL,
                confidence_score    FLOAT CHECK (confidence_score BETWEEN 0 AND 1),
                ping_count          INT DEFAULT 0,
                last_updated        TIMESTAMP DEFAULT NOW(),
                UNIQUE (store_id, item_id)
            )
        """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_store_inventory_map_location
                ON store_inventory_map USING GIST (estimated_loc_point)
        """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_store_inventory_map_store_confidence
                ON store_inventory_map (store_id, confidence_score)
        """);
    }

    private boolean isPostgreSQL() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect database metadata", exception);
        }
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void processAndCalculateCenters() {
        logger.info("Starting location centroid recalculation.");

        try {
            int deletedRows = jdbcTemplate.update("DELETE FROM store_inventory_map");
            logger.info("Deleted {} stale inventory map rows.", deletedRows);

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

            logger.info("Location recalculation finished successfully. Updated {} unique shelf locations.", insertedRows);

        } catch (Exception e) {
            logger.error("Failed to process location updates.", e);
            throw e;
        }
    }
}
