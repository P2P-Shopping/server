package com.p2ps.controller;

import com.p2ps.dto.TelemetryRequest;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryControllerTest {

    @Mock // Păcălim (Mock) conexiunea la baza de date
    private JdbcTemplate jdbcTemplate;

    @Mock // Păcălim repository-ul
    private StoreInventoryMapRepository mapRepository;

    @Mock // Păcălim worker-ul care rulează în fundal
    private LocationProcessorWorker worker;

    @InjectMocks // Injectăm mock-urile de mai sus în Controller-ul nostru real
    private TelemetryController telemetryController;

    @Test
    void testReceiveProductScan_Success() {
        // 1. Arrange: Pregătim datele false trimise de telefon
        TelemetryRequest request = new TelemetryRequest();
        request.setStoreId(UUID.randomUUID());
        request.setItemId(UUID.randomUUID());
        request.setLat(44.4268);
        request.setLon(26.1025);
        request.setAccuracy(5.0);

        // Îi spunem mock-ului ce să răspundă când Controller-ul încearcă să bage date
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()))
                .thenReturn(Optional.empty()); // Simulăm că produsul e nou și nu există în hartă

        // 2. Act: Rulăm metoda reală
        ResponseEntity<Void> response = telemetryController.receiveProductScan(request);

        // 3. Assert: Verificăm dacă rezultatul e cel așteptat (Status 200 OK)
        assertEquals(200, response.getStatusCode().value());

        // Ne asigurăm că interogarea de INSERT a fost apelată exact o dată
        verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any(), any(), any());
    }
}