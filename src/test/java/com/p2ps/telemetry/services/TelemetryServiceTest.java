package com.p2ps.telemetry.services;

import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.dto.TelemetryBatchDTO;
import com.p2ps.telemetry.model.TelemetryRecord;
import com.p2ps.telemetry.repository.TelemetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    private TelemetryRepository telemetryRepository;

    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryService = newTelemetryService(telemetryRepository);
    }

    @Test
    void shouldMapIncomingPingToTelemetryRecordAndSaveIt() {
        TelemetryPingDTO dto = buildPingDto();

        telemetryService.processPing(dto);

        ArgumentCaptor<TelemetryRecord> captor = ArgumentCaptor.forClass(TelemetryRecord.class);
        verify(telemetryRepository).save(captor.capture());

        TelemetryRecord saved = captor.getValue();
        assertEquals("device-1", ReflectionTestUtils.getField(saved, "deviceId"));
        assertEquals("store-7", ReflectionTestUtils.getField(saved, "storeId"));
        assertEquals("item-101", ReflectionTestUtils.getField(saved, "itemId"));
        assertEquals(47.151726, ReflectionTestUtils.getField(saved, "lat"));
        assertEquals(27.587914, ReflectionTestUtils.getField(saved, "lng"));
        assertEquals(3.5, ReflectionTestUtils.getField(saved, "accuracyMeters"));
        assertEquals(1711888658000L, ReflectionTestUtils.getField(saved, "timestamp"));
        assertNotNull(ReflectionTestUtils.getField(saved, "serverReceivedTimestamp"));
    }

    @Test
    void shouldHandleRepositoryFailureWithoutThrowing() {
        TelemetryPingDTO dto = buildPingDto();
        doThrow(new RuntimeException("Mongo unavailable")).when(telemetryRepository).save(org.mockito.ArgumentMatchers.any());

        telemetryService.processPing(dto);

        verify(telemetryRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldAllowNullFieldsAndPersistRecordWithNullValues() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        ReflectionTestUtils.setField(dto, "itemId", "item-202");

        telemetryService.processPing(dto);

        ArgumentCaptor<TelemetryRecord> captor = ArgumentCaptor.forClass(TelemetryRecord.class);
        verify(telemetryRepository).save(captor.capture());

        TelemetryRecord saved = captor.getValue();
        assertNull(ReflectionTestUtils.getField(saved, "deviceId"));
        assertNull(ReflectionTestUtils.getField(saved, "storeId"));
        assertEquals("item-202", ReflectionTestUtils.getField(saved, "itemId"));
        assertNull(ReflectionTestUtils.getField(saved, "lat"));
        assertNull(ReflectionTestUtils.getField(saved, "lng"));
        assertNull(ReflectionTestUtils.getField(saved, "accuracyMeters"));
        assertNull(ReflectionTestUtils.getField(saved, "timestamp"));
        assertNotNull(ReflectionTestUtils.getField(saved, "serverReceivedTimestamp"));
    }

    @Test
    void shouldMapIncomingBatchToTelemetryRecordsAndInsertThem() {
        TelemetryBatchDTO batchDTO = new TelemetryBatchDTO();
        ReflectionTestUtils.setField(batchDTO, "pings", List.of(buildPingDto(), buildPingDto()));

        telemetryService.processBatch(batchDTO);

        ArgumentCaptor<List<TelemetryRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(telemetryRepository).insert(captor.capture());

        java.util.List<TelemetryRecord> records = captor.getValue();
        assertEquals(2, records.size());
        assertEquals("device-1", ReflectionTestUtils.getField(records.get(0), "deviceId"));
        assertEquals("item-101", ReflectionTestUtils.getField(records.get(1), "itemId"));
        assertNotNull(ReflectionTestUtils.getField(records.get(0), "serverReceivedTimestamp"));
        assertNotNull(ReflectionTestUtils.getField(records.get(1), "serverReceivedTimestamp"));
    }

    @Test
    void shouldHandleBatchRepositoryFailureWithoutThrowing() {
        TelemetryBatchDTO batchDTO = new TelemetryBatchDTO();
        ReflectionTestUtils.setField(batchDTO, "pings", List.of(buildPingDto()));
        doThrow(new RuntimeException("Mongo unavailable")).when(telemetryRepository).insert(org.mockito.ArgumentMatchers.anyList());

        telemetryService.processBatch(batchDTO);

        verify(telemetryRepository).insert(org.mockito.ArgumentMatchers.anyList());
    }

    private TelemetryPingDTO buildPingDto() {
        TelemetryPingDTO dto = new TelemetryPingDTO();
        ReflectionTestUtils.setField(dto, "deviceId", "device-1");
        ReflectionTestUtils.setField(dto, "storeId", "store-7");
        ReflectionTestUtils.setField(dto, "itemId", "item-101");
        ReflectionTestUtils.setField(dto, "lat", 47.151726);
        ReflectionTestUtils.setField(dto, "lng", 27.587914);
        ReflectionTestUtils.setField(dto, "accuracyMeters", 3.5);
        ReflectionTestUtils.setField(dto, "timestamp", 1711888658000L);
        return dto;
    }

    private TelemetryService newTelemetryService(TelemetryRepository repository) {
        try {
            Constructor<TelemetryService> constructor = TelemetryService.class.getDeclaredConstructor(TelemetryRepository.class);
            constructor.setAccessible(true);
            return constructor.newInstance(repository);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create TelemetryService", e);
        }
    }
}
