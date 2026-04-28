package com.p2ps.telemetry.services;

import com.p2ps.telemetry.repository.TelemetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryCleanupJobTest {

    @Mock
    private TelemetryRepository telemetryRepository;

    private TelemetryCleanupJob cleanupJob;

    @BeforeEach
    void setUp() {
        cleanupJob = new TelemetryCleanupJob(telemetryRepository);
        ReflectionTestUtils.setField(cleanupJob, "retentionDays", 7);
    }

    @Test
    void shouldDeleteRecordsOlderThanRetentionPeriod() {
        cleanupJob.deleteStaleRecords();

        verify(telemetryRepository).deleteByTimestampBefore(anyLong());
    }

    @Test
    void shouldUseCutoffOlderThan7Days() {
        long before = System.currentTimeMillis();
        cleanupJob.deleteStaleRecords();
        long after = System.currentTimeMillis();

        org.mockito.ArgumentCaptor<Long> captor = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(telemetryRepository).deleteByTimestampBefore(captor.capture());

        long cutoff = captor.getValue();
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;

        assert cutoff <= before - sevenDaysMs;
        assert cutoff >= after - sevenDaysMs - 1000;
    }
}