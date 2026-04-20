package com.p2ps.telemetry.controller;

import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.telemetry.dto.TelemetryRequest;
import com.p2ps.telemetry.model.TelemetryRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.dto.TelemetryBatchDTO;
import com.p2ps.telemetry.services.TelemetryService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
@Slf4j
public class TelemetryController {

    private final TelemetryService telemetryService;
    private final JdbcTemplate jdbcTemplate;
    private final StoreInventoryMapRepository mapRepository;
    private final LocationProcessorWorker locationProcessorWorker;

    @PostMapping("/ping")
    public ResponseEntity<Map<String, String>> receivePing(@Valid @RequestBody TelemetryPingDTO pingDTO) {
        log.info("[API] Ping received for the product: {}", pingDTO.getItemId());
        telemetryService.processPing(pingDTO);
        return ResponseEntity.accepted().body(Map.of("status", "success"));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, String>> receiveBatch(@Valid @RequestBody TelemetryBatchDTO batchDTO) {
        log.info("[API] Batch received with {} pings", batchDTO.getPings().size());
        telemetryService.processBatch(batchDTO);
        return ResponseEntity.accepted().body(Map.of("status", "success"));
    }

    @GetMapping("/pings")
    public ResponseEntity<List<TelemetryRecord>> getPings(
            @RequestParam String storeId,
            @RequestParam String itemId) {
        log.info("[API] GET pings for storeId: {}, itemId: {}", storeId, itemId);
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
            log.error("❌ Eroare la salvarea ping-ului brut pentru item {}: {}", request.getItemId(), e.getMessage());
            return ResponseEntity.internalServerError().body("Eroare la procesarea datelor.");
        }

        mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()).ifPresent(map -> {
            if (map.getEstimatedLocPoint() != null) {
                double distance = calculateHaversineDistance(
                        request.getLat(), request.getLon(),
                        map.getEstimatedLocPoint().getY(), map.getEstimatedLocPoint().getX()
                );

                if (distance > 15.0) {
                    log.warn("⚠️ Produs mutat detectat ({}m). Declanșăm recalcularea pentru: {}", (int) distance, request.getItemId());

                    try {
                        locationProcessorWorker.recalculateSingleItem(request.getStoreId(), request.getItemId());
                    } catch (TaskRejectedException e) {
                        log.error("🚫 Coada de execuție asincronă este plină. Recalcularea pentru {} a fost amânată.", request.getItemId());
                    }
                }
            }
        });

        return ResponseEntity.ok().build();
    }

    @PostMapping("/admin/sync-centers")
    public ResponseEntity<String> triggerGlobalSync() {
        log.info("🚀 [Admin] Pornire manuală a sincronizării globale a centrelor.");
        try {
            locationProcessorWorker.processAndCalculateCenters();
            return ResponseEntity.ok("Sincronizarea globală a fost pornită cu succes.");
        } catch (Exception e) {
            log.error("❌ Eroare la sincronizarea globală: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Eroare la sincronizare.");
        }
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
