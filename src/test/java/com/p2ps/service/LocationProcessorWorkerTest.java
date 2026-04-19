package com.p2ps.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
    "telemetry.api.key=test-telemetry-key-for-tests",
    "app.scheduling.enabled=false"
})
@Transactional
class LocationProcessorWorkerTest {

    static DockerImageName postgisImage = DockerImageName.parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(postgisImage)
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    static {
        postgresContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private FailureAwareJdbcTemplate jdbcTemplate;

    @Autowired
    private LocationProcessorWorker worker;

    @BeforeEach
    void resetJdbcTemplateFailureState() {
        jdbcTemplate.setFailOnInsert(false);
    }

    @Test
    @DisplayName("Must successfully execute DELETE followed by INSERT for center recalculation")
    void processAndCalculateCenters_Success() {
        ensureSpatialTables();
        jdbcTemplate.setFailOnInsert(false);

        UUID storeId = UUID.randomUUID();
        long userId = 12345L;
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO store_geofences (store_id, name, boundary_polygon) VALUES (?, 'Magazin Test', ST_GeomFromText('POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))', 4326))", storeId);
        jdbcTemplate.update("INSERT INTO users (id, first_name, last_name, email, password) VALUES (?, 'Test', 'User', 'test@example.com', 'pass')", userId);
        jdbcTemplate.update("INSERT INTO shopping_lists (id, title, user_id) VALUES (?, 'Lista mea', ?)", listId, userId);
        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id) VALUES (?, 'Lapte', false, ?)", itemId, listId);

        for (int index = 0; index < 10; index++) {
            jdbcTemplate.update(
                    "INSERT INTO raw_user_pings (store_id, item_id, location_point, accuracy_m, loc_provider) VALUES (?, ?, ST_SetSRID(ST_MakePoint(27.587, 47.151), 4326), ?, ?)",
                    storeId,
                    itemId,
                    5.0,
                    "GPS"
            );
        }

        worker.processAndCalculateCenters();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ping_count, confidence_score FROM store_inventory_map WHERE store_id = ? AND item_id = ?",
                storeId,
                itemId
        );

        assertTrue(!rows.isEmpty(), "There must be at least one calculated row for the store/item combination");

        Number pingCount = (Number) rows.get(0).get("ping_count");
        Number confidenceScore = (Number) rows.get(0).get("confidence_score");

        assertTrue(pingCount != null && pingCount.intValue() >= 10, "The ping count should reflect the cluster of inserted pings");
        assertTrue(confidenceScore != null && confidenceScore.doubleValue() >= 0.0 && confidenceScore.doubleValue() <= 1.0, "The confidence score should be within a plausible range");
    }

    @Test
    @DisplayName("Must throw exception further if SQL query fails (to trigger Rollback)")
    void processAndCalculateCenters_ThrowsExceptionOnError() {
        jdbcTemplate.setFailOnInsert(true);

        try {
            assertThrows(RuntimeException.class, () -> worker.processAndCalculateCenters());
        } finally {
            jdbcTemplate.setFailOnInsert(false);
        }
    }

    private void ensureSpatialTables() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS store_geofences (
                store_id UUID PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                boundary_polygon GEOMETRY(Polygon, 4326) NOT NULL
            )
        """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS raw_user_pings (
                ping_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                store_id UUID NOT NULL REFERENCES store_geofences(store_id),
                item_id UUID NOT NULL REFERENCES items(id),
                location_point GEOMETRY(Point, 4326) NOT NULL,
                accuracy_m FLOAT,
                loc_provider VARCHAR(50)
            )
        """);
        jdbcTemplate.execute("ALTER TABLE store_inventory_map ALTER COLUMN map_id SET DEFAULT gen_random_uuid()");
        jdbcTemplate.execute("ALTER TABLE store_inventory_map ALTER COLUMN last_updated SET DEFAULT CURRENT_TIMESTAMP");
    }

    @TestConfiguration
    static class JdbcTemplateTestConfiguration {

        @Bean
        @Primary
        FailureAwareJdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new FailureAwareJdbcTemplate(dataSource);
        }
    }

    static class FailureAwareJdbcTemplate extends JdbcTemplate {

        private final AtomicBoolean failOnInsert = new AtomicBoolean(false);

        FailureAwareJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        void setFailOnInsert(boolean failOnInsert) {
            this.failOnInsert.set(failOnInsert);
        }

        @Override
        public int update(String sql) {
            if (failOnInsert.get() && sql.contains("INSERT INTO store_inventory_map")) {
                throw new RuntimeException("forced insert failure");
            }
            return super.update(sql);
        }
    }
}
