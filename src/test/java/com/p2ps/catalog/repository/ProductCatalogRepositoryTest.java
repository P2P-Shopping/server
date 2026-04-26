package com.p2ps.catalog.repository;

import com.p2ps.catalog.model.ProductCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
    "telemetry.api.key=test-telemetry-key-for-tests",
    "app.scheduling.enabled=false"
})
@Transactional
class ProductCatalogRepositoryTest {

    static DockerImageName postgisImage = DockerImageName.parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(postgisImage)
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private ProductCatalogRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldFindBySpecificNameAndBrand() {
        ProductCatalog product = new ProductCatalog();
        product.setGenericName("Lapte");
        product.setSpecificName("Lapte Zuzu 1.5%");
        product.setBrand("Zuzu");
        product.setCategory("Lactate");
        product.setEstimatedPrice(new BigDecimal("7.50"));
        product.setPurchaseCount(10);
        
        repository.save(product);

        Optional<ProductCatalog> found = repository.findBySpecificNameAndBrand("Lapte Zuzu 1.5%", "Zuzu");
        
        assertTrue(found.isPresent());
        assertEquals("Lapte", found.get().getGenericName());
    }
    
    @Test
    void shouldFindBySpecificNameWhenBrandIsNull() {
        ProductCatalog product = new ProductCatalog();
        product.setGenericName("Rosii");
        product.setSpecificName("Rosii calitatea I");
        product.setBrand(null);
        product.setPurchaseCount(5);
        
        repository.save(product);

        Optional<ProductCatalog> found = repository.findBySpecificNameAndBrand("Rosii calitatea I", null);
        
        assertTrue(found.isPresent());
        assertEquals("Rosii", found.get().getGenericName());
    }

    @Test
    void shouldFindTop50ByOrderByPurchaseCountDesc() {
        // Using a specific prefix to avoid conflicts with 98-populate-catalog.sql if it runs
        for (int i = 1; i <= 55; i++) {
            ProductCatalog product = new ProductCatalog();
            product.setGenericName("TestGeneric " + i);
            product.setSpecificName("TestSpecific " + i);
            product.setBrand("TestBrand");
            product.setPurchaseCount(i + 1000); // Use very high purchase counts to guarantee they are the top 50
            repository.save(product);
        }

        List<ProductCatalog> topProducts = repository.findTop50ByOrderByPurchaseCountDesc();
        
        assertEquals(50, topProducts.size());
        // The first one should be the one with count 1055
        assertEquals("TestSpecific 55", topProducts.get(0).getSpecificName());
        // The 50th one should be the one with count 1006
        assertEquals("TestSpecific 6", topProducts.get(49).getSpecificName());
    }

    @Test
    void shouldFindBestStoresForCatalogProduct() {
        // Prepare DB schema for PostGIS & custom tables needed for the native query
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
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                first_name VARCHAR(255) NOT NULL,
                last_name VARCHAR(255) NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE,
                password VARCHAR(255) NOT NULL,
                token_version INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS shopping_lists (
                id UUID PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                user_id INTEGER NOT NULL REFERENCES users(id),
                category VARCHAR(50) NOT NULL DEFAULT 'NORMAL'
            )
        """);
        
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS store_inventory_map (
                map_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                store_id UUID NOT NULL REFERENCES store_geofences(store_id),
                item_id UUID NOT NULL,
                estimated_loc_point GEOMETRY(Point, 4326) NOT NULL,
                confidence_score FLOAT NOT NULL,
                ping_count INT NOT NULL,
                last_updated TIMESTAMP NOT NULL
            )
        """);

        UUID store1Id = UUID.randomUUID();
        UUID store2Id = UUID.randomUUID();
        
        jdbcTemplate.update("INSERT INTO store_geofences (store_id, name, boundary_polygon) VALUES (?, 'Store 1', ST_GeomFromText('POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))', 4326))", store1Id);
        jdbcTemplate.update("INSERT INTO store_geofences (store_id, name, boundary_polygon) VALUES (?, 'Store 2', ST_GeomFromText('POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))', 4326))", store2Id);

        jdbcTemplate.update("INSERT INTO users (id, first_name, last_name, email, password) VALUES (999, 'Test', 'User', 'catalog@example.com', 'pass') ON CONFLICT DO NOTHING");
        
        UUID listId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO shopping_lists (id, title, user_id) VALUES (?, 'List', 999)", listId);

        // Create the product catalog
        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setGenericName("Zahar");
        catalogProduct.setSpecificName("Zahar Margaritar 1kg");
        catalogProduct.setPurchaseCount(10);
        ProductCatalog savedCatalog = repository.save(catalogProduct);
        UUID catalogId = savedCatalog.getId();

        // Items mapped to the catalog product
        UUID item1Id = UUID.randomUUID();
        UUID item2Id = UUID.randomUUID();
        
        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id, catalog_id) VALUES (?, 'Zahar 1', false, ?, ?)", item1Id, listId, catalogId);
        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id, catalog_id) VALUES (?, 'Zahar 2', false, ?, ?)", item2Id, listId, catalogId);

        // Inventory mapping
        // Store 1 has item1 with confidence 0.9
        jdbcTemplate.update("INSERT INTO store_inventory_map (store_id, item_id, estimated_loc_point, confidence_score, ping_count, last_updated) VALUES (?, ?, ST_GeomFromText('POINT(0 0)', 4326), 0.9, 1, NOW())", store1Id, item1Id);
        
        // Store 2 has item2 with confidence 0.95
        jdbcTemplate.update("INSERT INTO store_inventory_map (store_id, item_id, estimated_loc_point, confidence_score, ping_count, last_updated) VALUES (?, ?, ST_GeomFromText('POINT(0 0)', 4326), 0.95, 1, NOW())", store2Id, item2Id);

        List<UUID> bestStores = repository.findBestStoresForCatalogProduct(catalogId);
        
        assertEquals(2, bestStores.size());
        // Since Store 2 has highest confidence (0.95 vs 0.9), it should be first
        assertEquals(store2Id, bestStores.get(0));
        assertEquals(store1Id, bestStores.get(1));
    }
}