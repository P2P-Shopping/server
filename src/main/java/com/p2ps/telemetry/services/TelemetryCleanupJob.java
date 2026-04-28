package com.p2ps.telemetry.services;

import com.p2ps.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelemetryCleanupJob {

    private final TelemetryRepository telemetryRepository;

    @Value("${telemetry.cleanup.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${telemetry.cleanup.cron:0 0 0 * * *}")
    public void deleteStaleRecords() {
        long cutoff = Instant.now()
                .minus(retentionDays, ChronoUnit.DAYS)
                .toEpochMilli();

        log.info("[CLEANUP] Deleting telemetry records older than {} days", retentionDays);
        telemetryRepository.deleteByTimestampBefore(cutoff);
        log.info("[CLEANUP] Done.");
    }
}