package com.p2ps.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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
    private DatabaseMetaData databaseMetaData;

    @InjectMocks
    private LocationProcessorWorker worker;

    @Test
    @DisplayName("Trebuie să execute cu succes DELETE și apoi INSERT pentru recalcularea centrelor")
    void processAndCalculateCenters_Success() {
        // Arrange: Simulăm comportamentul de succes al bazei de date
        when(jdbcTemplate.update(anyString())).thenReturn(5);

        // Act: Rulăm metoda worker-ului
        worker.processAndCalculateCenters();

        // Assert: Verificăm că jdbcTemplate a fost apelat exact cum ne așteptăm
        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să arunce excepția mai departe dacă interogarea SQL eșuează (pentru a declanșa Rollback)")
    void processAndCalculateCenters_ThrowsExceptionOnError() {
        // Arrange: Simulăm o eroare la pasul de INSERT
        when(jdbcTemplate.update(anyString()))
                .thenReturn(10)
                .thenThrow(new RuntimeException("Database error during insert"));

        // Act & Assert: Verificăm dacă excepția este corect propagată
        assertThrows(RuntimeException.class, () -> {
            worker.processAndCalculateCenters();
        });

        // Verify the calls were made
        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să creeze schema de inventar când baza de date este PostgreSQL")
    void ensureInventoryMapSchema_WhenPostgreSQL_ShouldCreateSchemaObjects() throws Exception {
        ReflectionTestUtils.setField(worker, "dataSource", dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL 16");

        worker.ensureInventoryMapSchema();

        verify(jdbcTemplate, atLeast(6)).execute(anyString());
    }

    @Test
    @DisplayName("Trebuie să ignore inițializarea când baza de date nu este PostgreSQL")
    void ensureInventoryMapSchema_WhenNotPostgreSQL_ShouldSkipSchemaCreation() throws Exception {
        ReflectionTestUtils.setField(worker, "dataSource", dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL 8.0");

        worker.ensureInventoryMapSchema();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Trebuie să transforme erorile de inspecție a bazei de date în IllegalStateException")
    void ensureInventoryMapSchema_WhenMetadataLookupFails_ShouldThrowIllegalStateException() throws Exception {
        ReflectionTestUtils.setField(worker, "dataSource", dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("metadata unavailable"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> worker.ensureInventoryMapSchema());

        assertEquals("Unable to inspect database metadata", exception.getMessage());
        verifyNoInteractions(jdbcTemplate);
    }
}
