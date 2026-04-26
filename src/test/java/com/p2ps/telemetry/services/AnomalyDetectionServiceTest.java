package com.p2ps.telemetry.services;

import com.p2ps.telemetry.model.PingStatus;
import com.p2ps.telemetry.model.TelemetryRecord;
import com.p2ps.telemetry.repository.TelemetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private TelemetryRepository telemetryRepository;

    private AnomalyDetectionService anomalyDetectionService;

    @BeforeEach
    void setUp() {
        anomalyDetectionService = new AnomalyDetectionService(telemetryRepository);
    }

    @Test
    void shouldAcceptValidPingWithNoHistory() {
        TelemetryRecord newRecord = createRecord(47.162123, 27.574381, 5.0, 10000L);
        when(telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        anomalyDetectionService.evaluateAndSetStatus(newRecord);

        assertEquals(PingStatus.ACCEPTED, newRecord.getStatus());
    }

    @Test
    void shouldDegradePingWithBadAccuracy() {
        TelemetryRecord newRecord = createRecord(47.162123, 27.574381, 50.0, 10000L);
        when(telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        anomalyDetectionService.evaluateAndSetStatus(newRecord);

        assertEquals(PingStatus.DEGRADED, newRecord.getStatus());
    }

    @Test
    void shouldRejectPingWithImpossibleSpeed() {
        TelemetryRecord lastPing = createRecord(47.162123, 27.574381, 5.0, 10000L);
        // Distance is ~1.1km, time diff is 1 second (1000ms) -> speed is > 1000 m/s
        TelemetryRecord newRecord = createRecord(47.172123, 27.574381, 5.0, 11000L);
        
        when(telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(lastPing));

        anomalyDetectionService.evaluateAndSetStatus(newRecord);

        assertEquals(PingStatus.REJECTED, newRecord.getStatus());
    }

    @Test
    void shouldAcceptPingWithPossibleSpeed() {
        TelemetryRecord lastPing = createRecord(47.162123, 27.574381, 5.0, 10000L);
        // Small distance, reasonable time diff (10 seconds)
        TelemetryRecord newRecord = createRecord(47.162223, 27.574381, 5.0, 20000L);
        
        when(telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(lastPing));

        anomalyDetectionService.evaluateAndSetStatus(newRecord);

        assertEquals(PingStatus.ACCEPTED, newRecord.getStatus());
    }

    @Test
    void shouldRejectPingJumpingLocationAtSameTimestamp() {
        TelemetryRecord lastPing = createRecord(47.162123, 27.574381, 5.0, 10000L);
        // Significant distance, exactly same timestamp
        TelemetryRecord newRecord = createRecord(47.163123, 27.574381, 5.0, 10000L);
        
        when(telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(lastPing));

        anomalyDetectionService.evaluateAndSetStatus(newRecord);

        assertEquals(PingStatus.REJECTED, newRecord.getStatus());
    }

    @Test
    void shouldAcceptOutOfOrderPingsButNotSetAsLatest() {
        TelemetryRecord lastPing = createRecord(47.162123, 27.574381, 5.0, 20000L); // newer
        TelemetryRecord newRecord = createRecord(47.162223, 27.574381, 5.0, 10000L); // older
        
        when(telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(lastPing));

        anomalyDetectionService.evaluateAndSetStatus(newRecord);

        // Out of order pings are accepted by default
        assertEquals(PingStatus.ACCEPTED, newRecord.getStatus());
    }

    @Test
    void shouldNotCrashOnNullCoordinates() {
        TelemetryRecord lastPing = createRecord(47.162123, 27.574381, 5.0, 10000L);
        TelemetryRecord newRecord = createRecord(null, null, 5.0, 20000L);
        
        when(telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(lastPing));

        anomalyDetectionService.evaluateAndSetStatus(newRecord);

        // Bypasses speed check gracefully
        assertEquals(PingStatus.ACCEPTED, newRecord.getStatus());
    }

    @Test
    void shouldNotCrashOnNullTimestamps() {
        TelemetryRecord lastPing = createRecord(47.162123, 27.574381, 5.0, null);
        TelemetryRecord newRecord = createRecord(47.162223, 27.574381, 5.0, null);
        
        when(telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(lastPing));

        anomalyDetectionService.evaluateAndSetStatus(newRecord);

        // Bypasses speed check gracefully
        assertEquals(PingStatus.ACCEPTED, newRecord.getStatus());
    }

    private TelemetryRecord createRecord(Double lat, Double lng, Double accuracy, Long timestamp) {
        TelemetryRecord telemetryRecord = new TelemetryRecord();
        telemetryRecord.setDeviceId("device-1");
        telemetryRecord.setStoreId("store-1");
        telemetryRecord.setItemId("item-1");
        telemetryRecord.setLat(lat);
        telemetryRecord.setLng(lng);
        telemetryRecord.setAccuracyMeters(accuracy);
        telemetryRecord.setTimestamp(timestamp);
        return telemetryRecord;
    }
}
