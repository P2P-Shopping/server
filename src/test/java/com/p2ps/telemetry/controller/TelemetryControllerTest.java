package com.p2ps.telemetry.controller;

import com.p2ps.entity.StoreInventoryMap;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.telemetry.dto.TelemetryRequest;
import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.dto.TelemetryBatchDTO;
import com.p2ps.telemetry.services.TelemetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class TelemetryControllerTest {

    private static final String RAW_USER_PING_INSERT_SQL = "INSERT INTO raw_user_pings (store_id, item_id, location_point, accuracy_m, loc_provider) " +
            "VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 'GPS')";

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StoreInventoryMapRepository mapRepository;

    @Mock
    private LocationProcessorWorker worker;

    @InjectMocks
    private TelemetryController telemetryController;

    @Test
    void shouldAcceptPingAndReturnSuccessStatus() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        ReflectionTestUtils.setField(dto, "deviceId", "device-1");
        ReflectionTestUtils.setField(dto, "storeId", "store-7");
        ReflectionTestUtils.setField(dto, "itemId", "item-101");
        ReflectionTestUtils.setField(dto, "lat", 47.151726);
        ReflectionTestUtils.setField(dto, "lng", 27.587914);
        ReflectionTestUtils.setField(dto, "accuracyMeters", 3.5);
        ReflectionTestUtils.setField(dto, "timestamp", 1711888658000L);

        ResponseEntity<Map<String, String>> response = telemetryController.receivePing(dto);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().get("status"));
        verify(telemetryService, times(1)).processPing(dto);
    }

    @Test
    void shouldAcceptPingWhenItemIdIsMissing() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        ReflectionTestUtils.setField(dto, "deviceId", "device-1");
        ReflectionTestUtils.setField(dto, "storeId", "store-7");

        ResponseEntity<Map<String, String>> response = telemetryController.receivePing(dto);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().get("status"));
        verify(telemetryService, times(1)).processPing(dto);
    }

    @Test
    void getPings_shouldReturn200WithRecords() {
        com.p2ps.telemetry.model.TelemetryRecord telemetryRecord = new com.p2ps.telemetry.model.TelemetryRecord();
        ReflectionTestUtils.setField(telemetryRecord, "storeId", "store-001");
        ReflectionTestUtils.setField(telemetryRecord, "itemId", "pasta");

        org.mockito.Mockito.when(telemetryService.getPings("store-001", "pasta"))
                .thenReturn(java.util.List.of(telemetryRecord));

        ResponseEntity<java.util.List<com.p2ps.telemetry.model.TelemetryRecord>> response =
                telemetryController.getPings("store-001", "pasta");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getPings_shouldReturnEmptyListWhenNoRecordsFound() {
        org.mockito.Mockito.when(telemetryService.getPings("store-999", "unknown"))
                .thenReturn(java.util.List.of());

        ResponseEntity<java.util.List<com.p2ps.telemetry.model.TelemetryRecord>> response =
                telemetryController.getPings("store-999", "unknown");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assert response.getBody() != null;
        assertEquals(0, response.getBody().size());
    }

    @Test
    void shouldAcceptBatchAndReturnSuccessStatus() {
        TelemetryPingDTO first = new TelemetryPingDTO();
        ReflectionTestUtils.setField(first, "deviceId", "device-1");
        ReflectionTestUtils.setField(first, "storeId", "store-7");
        ReflectionTestUtils.setField(first, "itemId", "item-101");
        ReflectionTestUtils.setField(first, "lat", 47.151726);
        ReflectionTestUtils.setField(first, "lng", 27.587914);
        ReflectionTestUtils.setField(first, "accuracyMeters", 3.5);
        ReflectionTestUtils.setField(first, "timestamp", 1711888658000L);

        TelemetryPingDTO second = new TelemetryPingDTO();
        ReflectionTestUtils.setField(second, "deviceId", "device-2");
        ReflectionTestUtils.setField(second, "storeId", "store-9");
        ReflectionTestUtils.setField(second, "itemId", "item-202");
        ReflectionTestUtils.setField(second, "lat", 47.152000);
        ReflectionTestUtils.setField(second, "lng", 27.588100);
        ReflectionTestUtils.setField(second, "accuracyMeters", 2.0);
        ReflectionTestUtils.setField(second, "timestamp", 1711888659000L);

        TelemetryBatchDTO batchDTO = new TelemetryBatchDTO();
        ReflectionTestUtils.setField(batchDTO, "pings", java.util.List.of(first, second));

        ResponseEntity<Map<String, String>> response = telemetryController.receiveBatch(batchDTO);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().get("status"));
        verify(telemetryService, times(1)).processBatch(batchDTO);
    }

    @Test
    void testReceiveProductScan_Success() {
        TelemetryRequest request = createRequest();

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = telemetryController.receiveProductScan(request);

        assertEquals(200, response.getStatusCode().value());
        verify(jdbcTemplate, times(1)).update(
                eq(RAW_USER_PING_INSERT_SQL),
                eq(request.getStoreId()),
                eq(request.getItemId()),
                eq(request.getLon()),
                eq(request.getLat()),
                eq(request.getAccuracy())
        );
    }

    @Test
    void testReceiveProductScan_RecalcNotTriggeredWhenNearbyMapExists() {
        TelemetryRequest request = createRequest();
        StoreInventoryMap existingMap = createExistingMap(request, 0.00005, 0.00000);

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()))
                .thenReturn(Optional.of(existingMap));

        ResponseEntity<?> response = telemetryController.receiveProductScan(request);

        assertEquals(200, response.getStatusCode().value());
        verify(jdbcTemplate, times(1)).update(
                eq(RAW_USER_PING_INSERT_SQL),
                eq(request.getStoreId()),
                eq(request.getItemId()),
                eq(request.getLon()),
                eq(request.getLat()),
                eq(request.getAccuracy())
        );
        verify(worker, never()).recalculateSingleItem(any(), any());
    }

    @Test
    void testReceiveProductScan_RecalcTriggeredWhenFarMapExists() {
        TelemetryRequest request = createRequest();
        StoreInventoryMap existingMap = createExistingMap(request, 0.00022, 0.0);

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()))
                .thenReturn(Optional.of(existingMap));

        ResponseEntity<?> response = telemetryController.receiveProductScan(request);

        assertEquals(200, response.getStatusCode().value());
        verify(jdbcTemplate, times(1)).update(
                eq(RAW_USER_PING_INSERT_SQL),
                eq(request.getStoreId()),
                eq(request.getItemId()),
                eq(request.getLon()),
                eq(request.getLat()),
                eq(request.getAccuracy())
        );
        verify(worker, times(1)).recalculateSingleItem(request.getStoreId(), request.getItemId());
    }

    private TelemetryRequest createRequest() {
        TelemetryRequest request = new TelemetryRequest();
        request.setStoreId(UUID.randomUUID());
        request.setItemId(UUID.randomUUID());
        request.setLat(44.4268);
        request.setLon(26.1025);
        request.setAccuracy(5.0);
        return request;
    }

    private StoreInventoryMap createExistingMap(TelemetryRequest request, double latOffset, double lonOffset) {
        Point estimatedPoint = GEOMETRY_FACTORY.createPoint(
                new Coordinate(request.getLon() + lonOffset, request.getLat() + latOffset)
        );
        estimatedPoint.setSRID(4326);

        StoreInventoryMap existingMap = new StoreInventoryMap();
        existingMap.setStoreId(request.getStoreId());
        existingMap.setItemId(request.getItemId());
        existingMap.setEstimatedLocPoint(estimatedPoint);
        return existingMap;
    }
}
