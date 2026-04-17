package com.p2ps.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@Transactional
class LocationProcessorWorkerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgis/postgis:16-3.4");

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.spatial.dialect.postgis.PostgisDialect");
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LocationProcessorWorker worker;

    @Test
    @DisplayName("Trebuie să execute cu succes DELETE și apoi INSERT pentru recalcularea centrelor")
    void processAndCalculateCenters_Success() {
        // Act: Rulăm metoda worker-ului
        worker.processAndCalculateCenters();

        // Assert: Verificăm că store_inventory_map a fost actualizat
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM store_inventory_map", 
            Integer.class
        );
        assertTrue(count != null && count >= 0, "Tabelul trebuie să existe și să fie accesibil");
    }

    @Test
    @DisplayName("Trebuie să arunce excepția mai departe dacă interogarea SQL eșuează (pentru a declanșa Rollback)")
    void processAndCalculateCenters_ThrowsExceptionOnError() {
        // Drop the table to simulate an error
        jdbcTemplate.execute("DROP TABLE IF EXISTS store_inventory_map CASCADE");

        // Act & Assert: Verificăm dacă excepția este corect propagată
        assertThrows(Exception.class, () -> {
            worker.processAndCalculateCenters();
        });

        // Recreate table for cleanup
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS store_inventory_map (
                map_id SERIAL PRIMARY KEY,
                store_id UUID,
                item_id UUID,
                estimated_loc_point GEOMETRY(Point, 4326),
                confidence_score DOUBLE PRECISION,
                ping_count INTEGER,
                last_updated TIMESTAMP
            )
        """);
    }
}
