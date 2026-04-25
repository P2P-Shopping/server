package com.p2ps.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreMatchingEngineTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private StoreMatchingEngine storeMatchingEngine;

    @Test
    void findOptimalStore_ShouldReturnNull_WhenItemIdsListIsNull() {
        // Act
        StoreMatchingEngine.StoreMatchResult result = storeMatchingEngine.findOptimalStore(47.15, 27.58, 5000, null);

        // Assert
        assertNull(result, "Rezultatul ar trebui să fie null când lista de produse este null.");
        verifyNoInteractions(jdbcTemplate); // Verificăm că baza de date nu a fost interogată
    }

    @Test
    void findOptimalStore_ShouldReturnNull_WhenItemIdsListIsEmpty() {
        // Act
        StoreMatchingEngine.StoreMatchResult result = storeMatchingEngine.findOptimalStore(47.15, 27.58, 5000, Collections.emptyList());

        // Assert
        assertNull(result, "Rezultatul ar trebui să fie null când lista de produse este goală.");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStore_ShouldReturnNull_WhenNoStoresFound() {
        // Arrange
        List<String> items = Arrays.asList("item1", "item2");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList()); // Simulăm lipsa rezultatelor din DB

        // Act
        StoreMatchingEngine.StoreMatchResult result = storeMatchingEngine.findOptimalStore(47.15, 27.58, 5000, items);

        // Assert
        assertNull(result, "Rezultatul ar trebui să fie null dacă interogarea nu întoarce nimic.");
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStore_ShouldReturnBestStore_WhenStoresAreFound() {
        // Arrange
        List<String> items = Arrays.asList("item1", "item2");
        double lat = 47.1585;
        double lng = 27.6014;
        double radius = 3000.0;

        StoreMatchingEngine.StoreMatchResult expectedStore =
                new StoreMatchingEngine.StoreMatchResult("store_123", "Supermarket Central", 2, 1200.5);

        // Simulăm un rezultat valid cu un magazin găsit
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.singletonList(expectedStore));

        // Act
        StoreMatchingEngine.StoreMatchResult actualStore = storeMatchingEngine.findOptimalStore(lat, lng, radius, items);

        // Assert
        assertNotNull(actualStore);
        assertEquals("store_123", actualStore.storeId());
        assertEquals("Supermarket Central", actualStore.storeName());
        assertEquals(2, actualStore.matchedItems());
        assertEquals(1200.5, actualStore.distanceMeters());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStore_ShouldPassCorrectArgumentsToJdbcTemplate() {
        // Arrange
        List<String> items = Arrays.asList("prod1", "prod2", "prod3");
        double lat = 47.1585;
        double lng = 27.6014;
        double radius = 1500.0;

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        // Act
        storeMatchingEngine.findOptimalStore(lat, lng, radius, items);

        // Assert
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), argsCaptor.capture());

        Object[] capturedArgs = argsCaptor.getValue();

        // Verificăm dimensiunea argumentelor:
        // 2 (makePoint 1: lng, lat) + 3 (items) + 3 (makePoint 2: lng, lat, radius) = 8 argumente
        assertEquals(8, capturedArgs.length);

        assertEquals(lng, capturedArgs[0]);
        assertEquals(lat, capturedArgs[1]);
        assertEquals("prod1", capturedArgs[2]);
        assertEquals("prod2", capturedArgs[3]);
        assertEquals("prod3", capturedArgs[4]);
        assertEquals(lng, capturedArgs[5]);
        assertEquals(lat, capturedArgs[6]);
        assertEquals(radius, capturedArgs[7]);
    }
}