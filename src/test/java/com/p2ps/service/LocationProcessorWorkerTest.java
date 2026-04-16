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
        when(jdbcTemplate.update(startsWith("DELETE FROM store_inventory_map"))).thenReturn(15);
        when(jdbcTemplate.update(startsWith("INSERT INTO store_inventory_map"))).thenReturn(5);

        // Act: Rulăm metoda worker-ului
        worker.processAndCalculateCenters();

        // Assert: Verificăm că jdbcTemplate a fost apelat exact cum ne așteptăm
        // Verificăm dacă a șters datele vechi (o singură dată)
        verify(jdbcTemplate, times(1)).update(startsWith("DELETE FROM store_inventory_map"));

        // Verificăm dacă a inserat noile date (o singură dată)
        verify(jdbcTemplate, times(1)).update(startsWith("INSERT INTO store_inventory_map"));
    }

    @Test
    @DisplayName("Trebuie să arunce excepția mai departe dacă interogarea SQL eșuează (pentru a declanșa Rollback)")
    void processAndCalculateCenters_ThrowsExceptionOnError() {
        // Arrange: Simulăm o eroare la pasul de INSERT (de ex: un timeout sau o problemă de sintaxă)
        when(jdbcTemplate.update(startsWith("DELETE FROM store_inventory_map"))).thenReturn(10);
        when(jdbcTemplate.update(startsWith("INSERT INTO store_inventory_map")))
                .thenThrow(new RuntimeException("Database error during insert"));

        // Act & Assert: Verificăm dacă excepția este corect propagată mai departe de try-catch
        assertThrows(RuntimeException.class, () -> {
            worker.processAndCalculateCenters();
        });

        // Verificăm că metoda a încercat să șteargă
        verify(jdbcTemplate, times(1)).update(startsWith("DELETE FROM store_inventory_map"));
        // Verificăm că metoda a încercat să insereze (și aici a crăpat)
        verify(jdbcTemplate, times(1)).update(startsWith("INSERT INTO store_inventory_map"));
    }
}