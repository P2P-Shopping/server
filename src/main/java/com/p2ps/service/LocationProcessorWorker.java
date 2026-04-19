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

    public LocationProcessorWorker(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    /**
     * Constructor allowing optional DataSource injection. Spring will prefer
     * the two-arg constructor when a DataSource bean is available, otherwise
     * fall back to the single-arg constructor.
     */
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
