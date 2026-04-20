package com.p2ps.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocationProcessorWorker {

    private static final Logger log = LoggerFactory.getLogger(LocationProcessorWorker.class);
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Autowired
    public LocationProcessorWorker(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    /**
     * Se asigură că extensiile PostGIS și tabelele există la startup.
     */
    @PostConstruct
    public void ensureInventoryMapSchema() {
        if (dataSource == null || !isPostgreSQL()) return;

        log.info("Checking and initializing spatial schema...");
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
                CONSTRAINT uk_store_item UNIQUE (store_id, item_id)
            )
        """);

        int removedDuplicates = jdbcTemplate.update("""
            WITH ranked_rows AS (
                SELECT ctid,
                       ROW_NUMBER() OVER (
                           PARTITION BY store_id, item_id
                           ORDER BY last_updated DESC NULLS LAST, ctid DESC
                       ) AS row_num
                FROM store_inventory_map
            )
            DELETE FROM store_inventory_map sim
            USING ranked_rows
            WHERE sim.ctid = ranked_rows.ctid
              AND ranked_rows.row_num > 1
        """);

        if (removedDuplicates > 0) {
            log.warn("Removed {} duplicate store_inventory_map rows before enforcing the unique constraint.", removedDuplicates);
        }

        Boolean uniqueConstraintExists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM pg_constraint
                WHERE conname = 'uk_store_item'
                  AND conrelid = 'store_inventory_map'::regclass
            )
        """, Boolean.class);

        if (Boolean.FALSE.equals(uniqueConstraintExists)) {
            jdbcTemplate.execute("""
                ALTER TABLE store_inventory_map
                ADD CONSTRAINT uk_store_item UNIQUE (store_id, item_id)
            """);
        }

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_store_inventory_map_location ON store_inventory_map USING GIST (estimated_loc_point)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_store_inventory_map_store_confidence ON store_inventory_map (store_id, confidence_score)");
    }

    /**
     * Sincronizare globală a centrelor (Admin Task).
     * Folosește UPSERT și actualizează last_updated.
     */
    @Transactional
    public void processAndCalculateCenters() {
        log.info(" [Admin-Task] Începem sincronizarea globală a centrelor (Strategie UPSERT)...");

        String sql = """
            INSERT INTO store_inventory_map (store_id, item_id, estimated_loc_point, confidence_score, ping_count, last_updated)
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
            SELECT store_id, item_id, estimated_loc_point, confidence_score, ping_count, NOW()
            FROM RankedClusters
            ON CONFLICT (store_id, item_id) 
            DO UPDATE SET 
                estimated_loc_point = EXCLUDED.estimated_loc_point,
                confidence_score = EXCLUDED.confidence_score,
                ping_count = EXCLUDED.ping_count,
                last_updated = NOW();
        """;

        try {
            int affectedRows = jdbcTemplate.update(sql);
            log.info(" [Admin-Task] Sincronizare finalizată. {} rânduri procesate.", affectedRows);
        } catch (Exception e) {
            log.error(" [Admin-Task] Eroare critică la sincronizarea globală", e);
            throw e;
        }
    }

    /**
     * Recalculare asincronă pentru un singur produs.
     * Harden: min 5 puncte. Actualizează last_updated.
     */
    @Async("telemetryExecutor")
    @Transactional
    public void recalculateSingleItem(UUID storeId, UUID itemId) {
        log.info("⚡ [Rapid-Recalc] Analiză clustere pentru produsul: {}", itemId);
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
                HAVING COUNT(*) >= 5
                ORDER BY new_count DESC, new_conf DESC
                LIMIT 1
            )
            UPDATE store_inventory_map sim
            SET estimated_loc_point = bc.new_loc,
                confidence_score = bc.new_conf,
                ping_count = bc.new_count,
                last_updated = NOW()
            FROM BestCluster bc
            WHERE sim.store_id = ? AND sim.item_id = ?;
        """;

        try {
            int updated = jdbcTemplate.update(sql, storeId, itemId, storeId, itemId);
            if (updated > 0) {
                log.info(" [Rapid-Recalc] Poziție actualizată pentru: {}", itemId);
            } else {
                log.warn(" [Rapid-Recalc] Date insuficiente pentru item: {}", itemId);
            }
        } catch (Exception e) {
            log.error(" [Rapid-Recalc] Eroare la item " + itemId, e);
            throw e;
        }
    }

    /**
     * Data Decay Task: Curăță locațiile fantomă.
     * Rulează în fiecare noapte la ora 03:00.
     * Șterge produsele care nu au mai fost scanate/actualizate de mai mult de 7 zile.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void removeStaleLocations() {
        log.info(" [Data-Decay] Începem curățarea locațiilor învechite (neactualizate > 7 zile)...");

        try {
            String sql = "DELETE FROM store_inventory_map WHERE last_updated < NOW() - INTERVAL '7 days'";

            int deletedRows = jdbcTemplate.update(sql);
            if (deletedRows > 0) {
                log.info(" [Data-Decay] Am curățat {} locații inactive de pe hartă.", deletedRows);
            } else {
                log.info(" [Data-Decay] Nu s-au găsit date învechite. Harta este curată.");
            }
        } catch (Exception e) {
            log.error(" [Data-Decay] Eroare la ștergerea datelor vechi: ", e);
        }
    }

    private boolean isPostgreSQL() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect database metadata", exception);
        }
    }
}