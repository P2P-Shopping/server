package com.p2ps.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationProcessorWorkerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private LocationProcessorWorker worker;

    @Test
    @DisplayName("Trebuie să execute cu succes DELETE și apoi INSERT pentru recalcularea centrelor")
    void processAndCalculateCenters_Success() {
        when(jdbcTemplate.update(anyString())).thenReturn(5);

        worker.processAndCalculateCenters();

        verify(jdbcTemplate, times(2)).update(anyString());
    }

    @Test
    @DisplayName("Trebuie să arunce excepția mai departe dacă interogarea SQL eșuează (pentru a declanșa Rollback)")
    void processAndCalculateCenters_ThrowsExceptionOnError() {
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
    @DisplayName("Trebuie să execute rapid recalculation pentru un item")
    void recalculateSingleItem_ShouldPropagateFailure() {
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), eq(storeId), eq(itemId), eq(storeId), eq(itemId)))
                .thenThrow(new RuntimeException("Database error during update"));

        assertThrows(RuntimeException.class, () -> worker.recalculateSingleItem(storeId, itemId));

        verify(jdbcTemplate, times(1)).update(anyString(), eq(storeId), eq(itemId), eq(storeId), eq(itemId));
    }
}
