package com.p2ps.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
        List<UUID> items = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        when(namedJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        StoreMatchingEngine.StoreMatchResult result = storeMatchingEngine.findOptimalStore(47.15, 27.58, 5000, items);

        assertNull(result);
        verify(namedJdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStore_ShouldReturnBestStore_WhenStoresAreFound() {
        List<UUID> items = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        String storeId = UUID.randomUUID().toString();
        String storeName = "Supermarket Central";
        int matchedItems = 2;
        double distance = 1200.5;

        when(namedJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<StoreMatchingEngine.StoreMatchResult> mapper = invocation.getArgument(2);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("store_id")).thenReturn(storeId);
                    when(rs.getString("name")).thenReturn(storeName);
                    when(rs.getInt("matched_items")).thenReturn(matchedItems);
                    when(rs.getDouble("distance_m")).thenReturn(distance);
                    
                    return Collections.singletonList(mapper.mapRow(rs, 0));
                });

        StoreMatchingEngine.StoreMatchResult actualStore = storeMatchingEngine.findOptimalStore(47.1585, 27.6014, 3000.0, items);

        assertNotNull(actualStore);
        assertEquals(storeId, actualStore.storeId());
        assertEquals(storeName, actualStore.storeName());
        assertEquals(matchedItems, actualStore.matchedItems());
        assertEquals(distance, actualStore.distanceMeters());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStore_ShouldPassCorrectNamedParameters() {
        // Arrange
        List<UUID> items = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        double lat = 47.1585;
        double lng = 27.6014;
        double radiusMeters = 1500.0;
        
        // Conservative over-approximation calculation
        double cosLat = Math.cos(Math.toRadians(lat));
        double expectedRadiusDegrees = (radiusMeters / 111320.0) * (1.0 / Math.max(cosLat, 0.01)) * 1.02;

        when(namedJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        // Act
        storeMatchingEngine.findOptimalStore(lat, lng, radiusMeters, items);

        // Assert
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedJdbcTemplate).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));

        SqlParameterSource capturedParams = paramsCaptor.getValue();

        // Verify the named parameters mapped into the query
        assertEquals(lat, (Double) capturedParams.getValue("lat"), 0.0001);
        assertEquals(lng, (Double) capturedParams.getValue("lng"), 0.0001);
        assertEquals(radiusMeters, (Double) capturedParams.getValue("radiusMeters"), 0.0001);
        assertEquals(expectedRadiusDegrees, (Double) capturedParams.getValue("radiusDegrees"), 0.0001);
        assertEquals(items, capturedParams.getValue("itemIds"));
    }
}