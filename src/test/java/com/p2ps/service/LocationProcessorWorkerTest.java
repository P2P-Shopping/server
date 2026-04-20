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
        // [FIX] Am eliminat excluderea pentru MongoDB care cauza eroarea în IDE
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
    @DisplayName("Trebuie să execute cu succes UPSERT-ul (INSERT ON CONFLICT) pentru recalcularea centrelor")
    void processAndCalculateCenters_Success() {
        ensureSpatialTables();
        jdbcTemplate.setFailOnInsert(false);

        UUID storeId = UUID.randomUUID();
        long userId = 12345L;
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        // Pregătirea datelor (Foreign Keys)
        jdbcTemplate.update("INSERT INTO store_geofences (store_id, name, boundary_polygon) VALUES (?, 'Magazin Test', ST_GeomFromText('POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))', 4326))", storeId);
        jdbcTemplate.update("INSERT INTO users (id, first_name, last_name, email, password) VALUES (?, 'Test', 'User', 'test@example.com', 'pass')", userId);
        jdbcTemplate.update("INSERT INTO shopping_lists (id, title, user_id) VALUES (?, 'Lista mea', ?)", listId, userId);
        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id) VALUES (?, 'Lapte', false, ?)", itemId, listId);

        // Simulăm 10 ping-uri valide în același punct pentru a crea un cluster puternic
        for (int index = 0; index < 10; index++) {
            jdbcTemplate.update(
                    "INSERT INTO raw_user_pings (store_id, item_id, location_point, accuracy_m, loc_provider) VALUES (?, ?, ST_SetSRID(ST_MakePoint(27.587, 47.151), 4326), ?, ?)",
                    storeId,
                    itemId,
                    5.0,
                    "GPS"
            );
        }

        // Executăm logica de sincronizare (acum folosește UPSERT în loc de DELETE)
        worker.processAndCalculateCenters();

        // Verificăm dacă clusterul a fost calculat corect
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ping_count, confidence_score FROM store_inventory_map WHERE store_id = ? AND item_id = ?",
                storeId,
                itemId
        );

        assertTrue(!rows.isEmpty(), "Trebuie să existe cel puțin un rând calculat pentru combinația magazin/produs");

        Number pingCount = (Number) rows.get(0).get("ping_count");
        Number confidenceScore = (Number) rows.get(0).get("confidence_score");

        assertTrue(pingCount != null && pingCount.intValue() >= 10, "Numărul de ping-uri trebuie să reflecte clusterul inserat");
        assertTrue(confidenceScore != null && confidenceScore.doubleValue() >= 0.0 && confidenceScore.doubleValue() <= 1.0, "Scorul de încredere trebuie să fie într-un interval valid (0-1)");
    }

    @Test
    @DisplayName("Trebuie să arunce excepție dacă interogarea SQL eșuează (pentru a declanșa Rollback-ul)")
    void processAndCalculateCenters_ThrowsExceptionOnError() {
        // Fortăm o eroare în JdbcTemplate pentru a testa tranzacționalitatea
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

        // Tabele necesare pentru Foreign Keys din test
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

        // Asigurăm coloanele implicite necesare pe parcursul testelor
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

    /**
     * Un JdbcTemplate personalizat care ne permite să simulăm o cădere a bazei de date
     * în timpul testelor, pentru a verifica dacă @Transactional își face treaba (Rollback).
     */
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
            // Logica noastră din Worker folosește "INSERT INTO store_inventory_map ... ON CONFLICT"
            // Deci acest mock va intercepta corect și noua strategie de UPSERT
            if (failOnInsert.get() && sql.contains("INSERT INTO store_inventory_map")) {
                throw new RuntimeException("forced insert failure");
            }
            return super.update(sql);
        }
    }
}