package com.p2ps.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import com.p2ps.exception.RapidRecalculationException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LocationProcessorWorker {

    private static final Logger logger = LoggerFactory.getLogger(LocationProcessorWorker.class);
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.4d;
    private static final int MIN_PING_COUNT_FOR_CONFIDENCE = 5;
    private static final AtomicLong RAPID_RECALCULATION_FAILURES = new AtomicLong();

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

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
        if (dataSource == null) {
            return false;
        }

        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null
                    && productName.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException exception) {
            // Treat "cannot inspect metadata" as "not postgres" so the worker safely no-ops
            logger.warn("Unable to inspect database metadata; skipping location processing.", exception);
            return false;
        }
    }

    public boolean isLowConfidence(Double confidenceScore, Integer pingCount) {
        boolean confidenceTooLow = confidenceScore == null || confidenceScore < LOW_CONFIDENCE_THRESHOLD;
        boolean insufficientSignals = pingCount == null || pingCount < MIN_PING_COUNT_FOR_CONFIDENCE;
        return confidenceTooLow || insufficientSignals;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void processAndCalculateCenters() {
        if (!isPostgreSQL()) {
            return;
        }
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

    @Async("locationProcessorExecutor")
    @Transactional
    public CompletableFuture<Void> recalculateSingleItem(UUID storeId, UUID itemId) {
        if (!isPostgreSQL()) {
            logger.debug("Skipping rapid recalculation for item {} in store {} because database is not PostgreSQL.", itemId, storeId);
            return CompletableFuture.completedFuture(null);
        }
        logger.info("Starting rapid recalculation for item {} in store {}.", itemId, storeId);

        try {
            String sql = """
                WITH ItemPings AS (
                    SELECT location_point, accuracy_m
                    FROM raw_user_pings
                    WHERE store_id = ? AND item_id = ?
                      AND loc_provider IN ('WIFI_RTT', 'GPS')
                      AND accuracy_m < 12.0
                ),
                Clustered AS (
                    SELECT location_point, accuracy_m,
                           ST_ClusterDBSCAN(ST_Transform(location_point, 3857), eps := 3.0, minpoints := 3)
                           OVER () AS cluster_id
                    FROM ItemPings
                ),
                ClusterStats AS (
                    SELECT
                        cluster_id,
                        ST_GeometricMedian(ST_Collect(location_point)) AS estimated_loc_point,
                        LEAST(1.0, (COUNT(*) / 50.0) * (1.0 / GREATEST(1.0, AVG(accuracy_m)))) AS confidence_score,
                        COUNT(*) AS ping_count
                    FROM Clustered
                    WHERE cluster_id IS NOT NULL
                    GROUP BY cluster_id
                    ORDER BY ping_count DESC
                    LIMIT 1
                )
                UPDATE store_inventory_map inventory
                SET estimated_loc_point = COALESCE(stats.estimated_loc_point, inventory.estimated_loc_point),
                    confidence_score = COALESCE(stats.confidence_score, inventory.confidence_score),
                    ping_count = COALESCE(stats.ping_count, inventory.ping_count),
                    last_updated = NOW()
                FROM ClusterStats stats
                WHERE inventory.store_id = ? AND inventory.item_id = ?
            """;

            jdbcTemplate.update(sql, storeId, itemId, storeId, itemId);
            logger.info("Rapid recalculation finished for item {}.", itemId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            RAPID_RECALCULATION_FAILURES.incrementAndGet();
            logger.error("Rapid recalculation failed for item {}.", itemId, e);
            return CompletableFuture.failedFuture(new RapidRecalculationException("Rapid recalculation failed for item " + itemId, e));
        }
    }

    public static long getRapidRecalculationFailures() {
        return RAPID_RECALCULATION_FAILURES.get();
    }
}
