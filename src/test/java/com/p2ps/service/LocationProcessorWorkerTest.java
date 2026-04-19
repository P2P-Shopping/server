package com.p2ps.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationProcessorWorkerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LocationProcessorWorker worker;

    @Test
    @DisplayName("Trebuie să execute cu succes DELETE și apoi INSERT (Issue 2 Fix)")
    void processAndCalculateCenters_Success() {
        // Arrange
        when(jdbcTemplate.update(anyString()))
                .thenReturn(5)    // Primul apel: DELETE
                .thenReturn(10);  // Al doilea apel: INSERT

        // Act
        worker.processAndCalculateCenters();

        // Assert
        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să propage corect RuntimeException (Issue 1 Fix)")
    void processAndCalculateCenters_ThrowsException() {
        // Arrange
        when(jdbcTemplate.update(anyString())).thenThrow(new RuntimeException("DB Error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> worker.processAndCalculateCenters());
    }
}