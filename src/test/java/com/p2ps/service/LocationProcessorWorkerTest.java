package com.p2ps.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationProcessorWorkerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @InjectMocks
    private LocationProcessorWorker worker;

    @Test
    @DisplayName("Trebuie să execute cu succes DELETE și apoi INSERT pentru recalcularea centrelor")
    void processAndCalculateCenters_Success() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        when(jdbcTemplate.update(anyString())).thenReturn(5);

        worker.processAndCalculateCenters();

        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să arunce excepția mai departe dacă interogarea SQL eșuează (pentru a declanșa Rollback)")
    void processAndCalculateCenters_ThrowsExceptionOnError() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        when(jdbcTemplate.update(anyString()))
                .thenReturn(10)
                .thenThrow(new RuntimeException("Database error during insert"));

        assertThrows(RuntimeException.class, worker::processAndCalculateCenters);

        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să marcheze corect produsele cu confidence scăzut")
    void isLowConfidence_ShouldReflectThresholds() {
        assertTrue(worker.isLowConfidence(0.39d, 10));
        assertTrue(worker.isLowConfidence(0.8d, 4));
        assertFalse(worker.isLowConfidence(0.8d, 6));
    }

    @Test
    @DisplayName("Trebuie să trateze valorile lipsă ca low confidence")
    void isLowConfidence_ShouldTreatNullsAsLowConfidence() {
        assertTrue(worker.isLowConfidence(null, null));
        assertTrue(worker.isLowConfidence(null, 10));
        assertTrue(worker.isLowConfidence(0.8d, null));
    }

    @Test
    @DisplayName("Should propagate failure when rapid recalculation fails")
    void recalculateSingleItem_ShouldPropagateFailure() throws Exception {
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        reset(dataSource); // Resetați datasource dacă ar exista alte mock-uri preexistente din rulări
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        // Initialize the worker to detect database type
        worker.initialize();

        reset(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), eq(storeId), eq(itemId), eq(storeId), eq(itemId)))
                .thenThrow(new RuntimeException("Database error during update"));

        CompletableFuture<Void> future = worker.recalculateSingleItem(storeId, itemId);
        CompletionException ex = assertThrows(
                CompletionException.class,
                future::join
        );

        assertInstanceOf(com.p2ps.exception.RapidRecalculationException.class, ex.getCause());

        verify(jdbcTemplate, times(1)).update(anyString(), eq(storeId), eq(itemId), eq(storeId), eq(itemId));
    }

    @Test
    @DisplayName("Trebuie să returneze false dacă DataSource este null")
    void isPostgreSQL_NullDataSource() {
        LocationProcessorWorker workerNull = new LocationProcessorWorker(jdbcTemplate, null);
        workerNull.processAndCalculateCenters();
        verify(jdbcTemplate, never()).update(anyString());
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("Trebuie să returneze false dacă apare SQLException la detectDatabaseType")
    void isPostgreSQL_SQLException_At_Init() throws Exception {
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("Connection failed"));
        
        // Vrem să creăm un NOU worker specific acestui test, ca să aibă failure count-ul de la 0 la 3 intact
        LocationProcessorWorker customWorker = new LocationProcessorWorker(jdbcTemplate, dataSource);
        
        // Should catch the exception and disable postgres features
        customWorker.initialize();
        
        // This should short-circuit and not execute SQL
        customWorker.processAndCalculateCenters();
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să returneze false dacă apare SQLException in runtime")
    void isPostgreSQL_SQLException_Runtime() throws Exception {
        // Vrem să creăm un NOU worker specific acestui test
        LocationProcessorWorker customWorker = new LocationProcessorWorker(jdbcTemplate, dataSource);
        
        // Nu chemam initialize, vrem sa vedem cum se comporta isPostgreSQL cand prinde exceptia prima oara la un call de runtime
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("Connection failed"));
        customWorker.processAndCalculateCenters();
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să returneze false dacă nu este PostgreSQL")
    void isPostgreSQL_NotPostgres() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        worker.processAndCalculateCenters();
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să creeze schema dacă este PostgreSQL")
    void ensureInventoryMapSchema_Success() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        worker.initialize();

        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }

    @Test
    @DisplayName("Trebuie să nu facă nimic la PostConstruct dacă nu este PostgreSQL")
    void ensureInventoryMapSchema_NotPostgres() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        worker.initialize();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("Trebuie să execute rapid recalculation cu succes")
    void recalculateSingleItem_Success() throws Exception {
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        // Initialize the worker to detect database type
        worker.initialize();

        worker.recalculateSingleItem(storeId, itemId).join();

        verify(jdbcTemplate, times(1)).update(anyString(), eq(storeId), eq(itemId), eq(storeId), eq(itemId));
    }

    @Test
    @DisplayName("Trebuie să urmărească eșecurile de rapid recalculation")
    void getRapidRecalculationFailures_ShouldReflectCount() throws Exception {
        LocationProcessorWorker.resetRapidRecalculationFailures();
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        // Cream un worker nou si curat special pentru a nu mosteni un "postgresDetected = false" din rulari anterioare (static/state bleed)
        LocationProcessorWorker cleanWorker = new LocationProcessorWorker(jdbcTemplate, dataSource);
        
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        
        // Initialize the worker to detect database type
        cleanWorker.initialize();
        
        reset(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), eq(storeId), eq(itemId), eq(storeId), eq(itemId)))
                .thenThrow(new RuntimeException("Fail"));

        long before = LocationProcessorWorker.getRapidRecalculationFailures();
        CompletableFuture<Void> future = cleanWorker.recalculateSingleItem(storeId, itemId);
        assertThrows(CompletionException.class, future::join);
        assertEquals(before + 1, LocationProcessorWorker.getRapidRecalculationFailures());
    }

    @Test
    @DisplayName("Trebuie să sară peste recalculare dacă nu este PostgreSQL")
    void recalculateSingleItem_ShouldShortCircuit_WhenNotPostgres() throws Exception {
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        // Initialize the worker to detect database type
        worker.initialize();

        CompletableFuture<Void> future = worker.recalculateSingleItem(storeId, itemId);

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Trebuie să execute detectDatabaseType la startup")
    void detectDatabaseType_Success() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        worker.initialize();
        
        assertTrue(worker.isLowConfidence(0.1d, 1)); // Just a dummy call to isLowConfidence to show worker is active
        // Verify database type was detected correctly
        verify(connection, atLeastOnce()).getMetaData();
    }

    @Test
    @DisplayName("Trebuie să returneze false dacă DatabaseProductName este null")
    void checkIsPostgres_NullProductName() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(null);

        worker.processAndCalculateCenters();
        verify(jdbcTemplate, never()).update(anyString());
    }
}