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
    void findOptimalStores_ShouldReturnEmptyList_WhenItemIdsListIsNull() {
        List<StoreMatchingEngine.StoreMatchResult> result = storeMatchingEngine.findOptimalStores(47.15, 27.58, 5000, null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    void findOptimalStores_ShouldReturnEmptyList_WhenItemIdsListIsEmpty() {
        List<StoreMatchingEngine.StoreMatchResult> result = storeMatchingEngine.findOptimalStores(47.15, 27.58, 5000, Collections.emptyList());

        assertTrue(result.isEmpty());
        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStores_ShouldReturnEmptyList_WhenNoStoresFound() {
        List<UUID> items = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        when(namedJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        List<StoreMatchingEngine.StoreMatchResult> result = storeMatchingEngine.findOptimalStores(47.15, 27.58, 5000, items);

        assertTrue(result.isEmpty());
        verify(namedJdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStores_ShouldReturnTopThreeStores_WhenStoresAreFound() {
        List<UUID> items = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        String storeId1 = UUID.randomUUID().toString();
        String storeId2 = UUID.randomUUID().toString();
        String storeId3 = UUID.randomUUID().toString();

        when(namedJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<StoreMatchingEngine.StoreMatchResult> mapper = invocation.getArgument(2);
                    return List.of(
                            mapper.mapRow(mockResultSet(storeId1, "Supermarket Central", 3, 1200.5), 0),
                            mapper.mapRow(mockResultSet(storeId2, "Market Nord", 2, 1500.0), 1),
                            mapper.mapRow(mockResultSet(storeId3, "Fresh Corner", 1, 1800.25), 2)
                    );
                });

        List<StoreMatchingEngine.StoreMatchResult> actualStores = storeMatchingEngine.findOptimalStores(47.1585, 27.6014, 3000.0, items);

        assertEquals(3, actualStores.size());
        StoreMatchingEngine.StoreMatchResult firstStore = actualStores.getFirst();
        assertEquals(storeId1, firstStore.storeId());
        assertEquals("Supermarket Central", firstStore.storeName());
        assertEquals(3, firstStore.matchedItems());
        assertEquals(1200.5, firstStore.distanceMeters());
        assertEquals(storeId2, actualStores.get(1).storeId());
        assertEquals(storeId3, actualStores.get(2).storeId());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(namedJdbcTemplate).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("HAVING COUNT(sim.item_id) > 0"));
        assertTrue(sqlCaptor.getValue().contains("LIMIT 3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOptimalStores_ShouldPassCorrectNamedParameters() {
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
        storeMatchingEngine.findOptimalStores(lat, lng, radiusMeters, items);

        // Assert
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedJdbcTemplate).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));

        SqlParameterSource capturedParams = paramsCaptor.getValue();

        // Verify the named parameters mapped into the query
        Double capturedLat = (Double) capturedParams.getValue("lat");
        Double capturedLng = (Double) capturedParams.getValue("lng");
        Double capturedRadiusMeters = (Double) capturedParams.getValue("radiusMeters");
        Double capturedRadiusDegrees = (Double) capturedParams.getValue("radiusDegrees");

        assertNotNull(capturedLat);
        assertNotNull(capturedLng);
        assertNotNull(capturedRadiusMeters);
        assertNotNull(capturedRadiusDegrees);
        assertEquals(lat, capturedLat, 0.0001);
        assertEquals(lng, capturedLng, 0.0001);
        assertEquals(radiusMeters, capturedRadiusMeters, 0.0001);
        assertEquals(expectedRadiusDegrees, capturedRadiusDegrees, 0.0001);
        assertEquals(items, capturedParams.getValue("itemIds"));
    }

    private ResultSet mockResultSet(String storeId, String storeName, int matchedItems, double distanceMeters) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("store_id")).thenReturn(storeId);
        when(rs.getString("name")).thenReturn(storeName);
        when(rs.getInt("matched_items")).thenReturn(matchedItems);
        when(rs.getDouble("distance_m")).thenReturn(distanceMeters);
        return rs;
    }
}