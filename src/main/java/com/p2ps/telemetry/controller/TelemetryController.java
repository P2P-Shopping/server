package com.p2ps.telemetry.controller;

import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.telemetry.dto.TelemetryBatchDTO;
import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.dto.TelemetryRequest;
import com.p2ps.telemetry.model.TelemetryRecord;
import com.p2ps.telemetry.services.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
@Slf4j
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final LocationProcessorWorker locationProcessorWorker;
    private final StoreInventoryMapRepository mapRepository;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/ping")
    public ResponseEntity<Map<String, String>> receivePing(@Valid @RequestBody TelemetryPingDTO pingDTO) {
        log.info("[API] Received ping for item: {}", pingDTO.getItemId());

        telemetryService.processPing(pingDTO);

        try {
            maybeTriggerRapidRecalc(
                    UUID.fromString(pingDTO.getStoreId()),
                    UUID.fromString(pingDTO.getItemId()),
                    pingDTO.getLat(),
                    pingDTO.getLng()
            );
        } catch (IllegalArgumentException e) {
            log.error("[API] Invalid IDs received: store={}, item={}", pingDTO.getStoreId(), pingDTO.getItemId());
        } catch (Exception e) {
            log.error("[API] Error processing distance: {}", e.getMessage());
        }

        return ResponseEntity.accepted().body(Map.of("status", "success"));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, String>> receiveBatch(@Valid @RequestBody TelemetryBatchDTO batchDTO) {
        log.info("[API] Received batch with {} pings", batchDTO.getPings().size());
        telemetryService.processBatch(batchDTO);
        return ResponseEntity.accepted().body(Map.of("status", "success"));
    }

    @GetMapping("/pings")
    public ResponseEntity<List<TelemetryRecord>> getPings(
            @RequestParam String storeId,
            @RequestParam String itemId) {
        List<TelemetryRecord> records = telemetryService.getPings(storeId, itemId);
        return ResponseEntity.ok(records);
    }

    @PostMapping("/scan")
    public ResponseEntity<?> receiveProductScan(@Valid @RequestBody TelemetryRequest request) {
        String insertSql = "INSERT INTO raw_user_pings (store_id, item_id, location_point, accuracy_m, loc_provider) " +
                "VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 'GPS')";

        try {
            jdbcTemplate.update(insertSql,
                    request.getStoreId(),
                    request.getItemId(),
                    request.getLon(),
                    request.getLat(),
                    request.getAccuracy()
            );
        } catch (Exception e) {
            log.error("[API] Error saving raw ping for item {}: {}", request.getItemId(), e.getMessage());
            return ResponseEntity.internalServerError().body("Error processing data.");
        }

        try {
            maybeTriggerRapidRecalc(request.getStoreId(), request.getItemId(), request.getLat(), request.getLon());
        } catch (Exception e) {
            log.error("[API] Error processing distance for product scan {}: {}", request.getItemId(), e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/recalculate-all")
    public ResponseEntity<String> triggerGlobalRecalculation() {
        log.info("[Admin] Manually triggering global synchronization.");
        locationProcessorWorker.processAndCalculateCenters();
        return ResponseEntity.ok("The global synchronization process has started.");
    }

    private void maybeTriggerRapidRecalc(UUID storeUuid, UUID itemUuid, double lat, double lon) {
        mapRepository.findByStoreIdAndItemId(storeUuid, itemUuid).ifPresent(map -> {
            if (map.getEstimatedLocPoint() == null) {
                return;
            }

            double distance = calculateHaversineDistance(
                    lat, lon,
                    map.getEstimatedLocPoint().getY(), map.getEstimatedLocPoint().getX()
            );

            if (distance > 15.0) {
                log.warn("[API] Moved product detected ({}m). Item: {}", (int) distance, itemUuid);
                try {
                    locationProcessorWorker.recalculateSingleItem(storeUuid, itemUuid);
                } catch (TaskRejectedException e) {
                    log.error("[API] Processing queue is full for item: {}", itemUuid);
                }
            }
        });
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Raza Pământului în metri
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}