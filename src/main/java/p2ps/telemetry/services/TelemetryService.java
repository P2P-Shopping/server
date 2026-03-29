package p2ps.telemetry.services;

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

    @Async
    public void processPing(TelemetryPingDTO pingDTO) {
        log.info("[SERVICE] Processing ping for the product: {}", pingDTO.getItemId());

        TelemetryRecord record = new TelemetryRecord();
        record.setDeviceId(pingDTO.getDeviceId());
        record.setStoreId(pingDTO.getStoreId());
        record.setItemId(pingDTO.getItemId());
        record.setLat(pingDTO.getLat());
        record.setLng(pingDTO.getLng());
        record.setAccuracyMeters(pingDTO.getAccuracyMeters());
        record.setTimestamp(pingDTO.getTimestamp());
        record.setServerReceivedTimestamp(System.currentTimeMillis());

        telemetryRepository.save(record);

        log.info("[SERVICE] Ping successfully saved for product: {}", pingDTO.getItemId());
    }
}