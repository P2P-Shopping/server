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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class StoreInventoryMapRepositoryTest {

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

        // A. Inserăm un magazin fizic
        jdbcTemplate.update("INSERT INTO store_geofences (store_id, name, boundary_polygon) VALUES (?, 'Magazin Test', ST_GeomFromText('POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))', 4326))", storeId);

        // B. Inserăm un utilizator (pentru a putea crea lista de cumpărături)
        jdbcTemplate.update("INSERT INTO users (id, first_name, last_name, email, password) VALUES (999, 'Test', 'User', 'test@example.com', 'pass')");

        // C. Inserăm lista de cumpărături
        jdbcTemplate.update("INSERT INTO shopping_lists (id, title, user_id) VALUES (?, 'Lista mea', 999)", listId);

        // D. Inserăm produsul
        jdbcTemplate.update("INSERT INTO items (id, name, is_checked, list_id) VALUES (?, 'Lapte', false, ?)", itemId, listId);


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

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        int rowsUpdated = repository.applyDecayToOldRecords(0.1, cutoffDate);

        assertEquals(1, rowsUpdated, "Ar fi trebuit să se actualizeze exact un rând");

        StoreInventoryMap updatedProduct = repository.findById(oldProduct.getMapId()).orElseThrow();
        assertEquals(0.7, updatedProduct.getConfidenceScore(), 0.001, "Scorul trebuia să scadă de la 0.8 la 0.7");
    }
}