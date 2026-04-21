package com.p2ps.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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
}
