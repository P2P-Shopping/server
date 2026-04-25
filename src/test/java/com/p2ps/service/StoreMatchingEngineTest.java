package com.p2ps.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

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
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    @InjectMocks
    private StoreMatchingEngine storeMatchingEngine;

    @Test
    void findOptimalStore_ShouldReturnNull_WhenItemIdsListIsNull() {
        StoreMatchingEngine.StoreMatchResult result = storeMatchingEngine.findOptimalStore(47.15, 27.58, 5000, null);

        assertNull(result);
        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    void findOptimalStore_ShouldReturnNull_WhenItemIdsListIsEmpty() {
        StoreMatchingEngine.StoreMatchResult result = storeMatchingEngine.findOptimalStore(47.15, 27.58, 5000, Collections.emptyList());

        assertNull(result);
        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStore_ShouldReturnNull_WhenNoStoresFound() {
        List<String> items = Arrays.asList("item1", "item2");
        when(namedJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        StoreMatchingEngine.StoreMatchResult result = storeMatchingEngine.findOptimalStore(47.15, 27.58, 5000, items);

        assertNull(result);
        verify(namedJdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStore_ShouldReturnBestStore_WhenStoresAreFound() {
        List<String> items = Arrays.asList("item1", "item2");
        StoreMatchingEngine.StoreMatchResult expectedStore =
                new StoreMatchingEngine.StoreMatchResult("store_123", "Supermarket Central", 2, 1200.5);

        when(namedJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(expectedStore));

        StoreMatchingEngine.StoreMatchResult actualStore = storeMatchingEngine.findOptimalStore(47.1585, 27.6014, 3000.0, items);

        assertNotNull(actualStore);
        assertEquals("store_123", actualStore.storeId());
        assertEquals("Supermarket Central", actualStore.storeName());
        assertEquals(2, actualStore.matchedItems());
        assertEquals(1200.5, actualStore.distanceMeters());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStore_ShouldPassCorrectNamedParameters() {
        // Arrange
        List<String> items = Arrays.asList("prod1", "prod2", "prod3");
        double lat = 47.1585;
        double lng = 27.6014;
        double radiusMeters = 1500.0;
        double expectedRadiusDegrees = radiusMeters / 111320.0; // Constanta din clasa de service

        when(namedJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        // Act
        storeMatchingEngine.findOptimalStore(lat, lng, radiusMeters, items);

        // Assert
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedJdbcTemplate).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));

        MapSqlParameterSource capturedParams = (MapSqlParameterSource) paramsCaptor.getValue();

        // Verificăm maparea corectă a noilor parametri
        assertEquals(lat, capturedParams.getValue("lat"));
        assertEquals(lng, capturedParams.getValue("lng"));
        assertEquals(radiusMeters, capturedParams.getValue("radiusMeters"));
        assertEquals(expectedRadiusDegrees, capturedParams.getValue("radiusDegrees"));
        assertEquals(items, capturedParams.getValue("itemIds"));
    }
}