package com.p2ps.repository;

import com.p2ps.model.StoreInventoryMap;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
    "telemetry.api.key=test-telemetry-key-for-tests",
    "app.scheduling.enabled=false"
})
@Transactional
class StoreInventoryMapRepositoryTest {
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
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private StoreInventoryMapRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldDecreaseConfidenceScoreForOldRecords() {
        // ---  Construim Ierarhia pentru Foreign Keys ---
        UUID storeId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

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

        // A. Inserăm un magazin fizic
        jdbcTemplate.update("INSERT INTO store_geofences (store_id, name, boundary_polygon) VALUES (?, 'Magazin Test', ST_GeomFromText('POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))', 4326))", storeId);

        // B. Inserăm un utilizator (pentru a putea crea lista de cumpărături)
        jdbcTemplate.update("INSERT INTO users (id, first_name, last_name, email, password, token_version) VALUES (999, 'Test', 'User', 'test@example.com', 'pass', 0)");

        // C. Inserăm lista de cumpărături
        jdbcTemplate.update("INSERT INTO shopping_lists (id, title, user_id, category) VALUES (?, 'Lista mea', 999, 'NORMAL')", listId);

        // D. Inserăm produsul
        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id) VALUES (?, 'Lapte', false, ?)", itemId, listId);

        UUID lowConfidenceItemId = UUID.randomUUID();
        UUID exactCutoffItemId = UUID.randomUUID();
        UUID zeroConfidenceItemId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id) VALUES (?, 'Iaurt', false, ?)", lowConfidenceItemId, listId);
        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id) VALUES (?, 'Branza', false, ?)", exactCutoffItemId, listId);
        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id) VALUES (?, 'Apa', false, ?)", zeroConfidenceItemId, listId);


        // ---  (Pregătim locația produsului nostru) ---
        GeometryFactory geometryFactory = new GeometryFactory();
        Point dummyPoint = geometryFactory.createPoint(new Coordinate(27.587, 47.151));
        dummyPoint.setSRID(4326);

        StoreInventoryMap oldProduct = new StoreInventoryMap();

        // FOLOSIM ID-urile generate mai sus
        oldProduct.setStoreId(storeId);
        oldProduct.setItemId(itemId);

        oldProduct.setEstimatedLocPoint(dummyPoint);
        oldProduct.setConfidenceScore(0.8);
        oldProduct.setPingCount(10);
        oldProduct.setLastUpdated(LocalDateTime.now().minusDays(10));

        repository.save(oldProduct);

        StoreInventoryMap lowConfidenceProduct = new StoreInventoryMap();
        lowConfidenceProduct.setStoreId(storeId);
        lowConfidenceProduct.setItemId(lowConfidenceItemId);
        lowConfidenceProduct.setEstimatedLocPoint(dummyPoint);
        lowConfidenceProduct.setConfidenceScore(0.2);
        lowConfidenceProduct.setPingCount(10);
        lowConfidenceProduct.setLastUpdated(LocalDateTime.now().minusDays(10));

        repository.save(lowConfidenceProduct);

        StoreInventoryMap exactCutoffProduct = new StoreInventoryMap();
        exactCutoffProduct.setStoreId(storeId);
        exactCutoffProduct.setItemId(exactCutoffItemId);
        exactCutoffProduct.setEstimatedLocPoint(dummyPoint);
        exactCutoffProduct.setConfidenceScore(0.6);
        exactCutoffProduct.setPingCount(10);
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        exactCutoffProduct.setLastUpdated(cutoffDate);

        repository.save(exactCutoffProduct);

        StoreInventoryMap zeroConfidenceProduct = new StoreInventoryMap();
        zeroConfidenceProduct.setStoreId(storeId);
        zeroConfidenceProduct.setItemId(zeroConfidenceItemId);
        zeroConfidenceProduct.setEstimatedLocPoint(dummyPoint);
        zeroConfidenceProduct.setConfidenceScore(0.0);
        zeroConfidenceProduct.setPingCount(10);
        zeroConfidenceProduct.setLastUpdated(LocalDateTime.now().minusDays(10));

        repository.save(zeroConfidenceProduct);

        int rowsUpdated = repository.applyDecayToOldRecords(0.1, cutoffDate, 0.15);

        assertEquals(2, rowsUpdated, "Ar fi trebuit să se actualizeze exact două rânduri");

        StoreInventoryMap updatedProduct = repository.findById(oldProduct.getMapId()).orElseThrow();
        assertEquals(0.7, updatedProduct.getConfidenceScore(), 0.001, "Scorul trebuia să scadă de la 0.8 la 0.7");

        StoreInventoryMap clampedProduct = repository.findById(lowConfidenceProduct.getMapId()).orElseThrow();
        assertEquals(0.15, clampedProduct.getConfidenceScore(), 0.001, "Scorul trebuia să fie clamped la 0.15");

        StoreInventoryMap unchangedCutoffProduct = repository.findById(exactCutoffProduct.getMapId()).orElseThrow();
        assertEquals(0.6, unchangedCutoffProduct.getConfidenceScore(), 0.001, "Rândul de la cutoff nu trebuia modificat");

        StoreInventoryMap unchangedZeroConfidenceProduct = repository.findById(zeroConfidenceProduct.getMapId()).orElseThrow();
        assertEquals(0.0, unchangedZeroConfidenceProduct.getConfidenceScore(), 0.001, "Rândul cu confidence 0 nu trebuia modificat");
    }
}