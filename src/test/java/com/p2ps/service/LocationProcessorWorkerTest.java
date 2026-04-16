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
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationProcessorWorkerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

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
}
