package p2ps.telemetry.services;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import p2ps.telemetry.dto.TelemetryPingDTO;
import p2ps.telemetry.model.TelemetryRecord;
import p2ps.telemetry.repository.TelemetryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;

    @Async("telemetryExecutor")
    public void processPing(TelemetryPingDTO pingDTO) {
        log.info("[SERVICE] Processing ping for the product: {}", pingDTO.getItemId());

        TelemetryRecord telemetryRecord = new TelemetryRecord();
        telemetryRecord.setDeviceId(pingDTO.getDeviceId());
        telemetryRecord.setStoreId(pingDTO.getStoreId());
        telemetryRecord.setItemId(pingDTO.getItemId());
        telemetryRecord.setLat(pingDTO.getLat());
        telemetryRecord.setLng(pingDTO.getLng());
        telemetryRecord.setAccuracyMeters(pingDTO.getAccuracyMeters());
        telemetryRecord.setTimestamp(pingDTO.getTimestamp());
        telemetryRecord.setServerReceivedTimestamp(Instant.now());

        try {
            telemetryRepository.save(telemetryRecord);
            log.info("[SERVICE] Ping successfully saved for product: {}", pingDTO.getItemId());
        } catch (Exception e) {
            log.error("[SERVICE] Failed to save ping: {}", e.getMessage(), e);
        }
    }
}
