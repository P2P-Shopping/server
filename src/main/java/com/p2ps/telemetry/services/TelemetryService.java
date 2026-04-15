package com.p2ps.telemetry.services;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.dto.TelemetryBatchDTO;
import com.p2ps.telemetry.model.TelemetryRecord;
import com.p2ps.telemetry.repository.TelemetryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;

    @Async("telemetryExecutor")
    public void processPing(TelemetryPingDTO pingDTO) {
        log.info("[SERVICE] Processing ping for the product: {}", pingDTO.getItemId());

        TelemetryRecord telemetryRecord = mapToEntity(pingDTO);

        try {
            telemetryRepository.save(telemetryRecord);
            log.info("[SERVICE] Ping successfully saved for product: {}", pingDTO.getItemId());
        } catch (Exception e) {
            log.error("[SERVICE] Failed to save ping: {}", e.getMessage(), e);
        }
    }

    @Async("telemetryExecutor")
    public void processBatch(TelemetryBatchDTO batchDTO) {
        log.info("[SERVICE] Processing batch of {} pings", batchDTO.getPings().size());

        List<TelemetryRecord> records = batchDTO.getPings().stream()
                .map(this::mapToEntity)
                .toList();

        try {
            // Bulk native insert in MongoDB
            telemetryRepository.insert(records);
            log.info("[SERVICE] Batch successfully saved!");
        } catch (Exception e) {
            log.error("[SERVICE] Failed to save batch: {}", e.getMessage(), e);
        }
    }

    private TelemetryRecord mapToEntity(TelemetryPingDTO pingDTO) {
        TelemetryRecord telemetryRecord = new TelemetryRecord();
        telemetryRecord.setDeviceId(pingDTO.getDeviceId());
        telemetryRecord.setStoreId(pingDTO.getStoreId());
        telemetryRecord.setItemId(pingDTO.getItemId());
        telemetryRecord.setLat(pingDTO.getLat());
        telemetryRecord.setLng(pingDTO.getLng());
        telemetryRecord.setAccuracyMeters(pingDTO.getAccuracyMeters());
        telemetryRecord.setTimestamp(pingDTO.getTimestamp());
        telemetryRecord.setServerReceivedTimestamp(Instant.now());
        return telemetryRecord;
    }

    public List<TelemetryRecord> getPings(String storeId, String itemId) {
        log.info("[SERVICE] Getting pings for storeId: {}, itemId: {}", storeId, itemId);
        return telemetryRepository.findByStoreIdAndItemId(storeId, itemId);
    }
}
