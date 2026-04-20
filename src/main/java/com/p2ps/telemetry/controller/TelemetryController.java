package com.p2ps.telemetry.controller;

import com.p2ps.telemetry.model.TelemetryRecord;
import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.dto.TelemetryBatchDTO;
import com.p2ps.telemetry.services.TelemetryService;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.repository.StoreInventoryMapRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.task.TaskRejectedException;

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


    @PostMapping("/ping")
    public ResponseEntity<Map<String, String>> receivePing(@Valid @RequestBody TelemetryPingDTO pingDTO) {
        log.info("[API] Ping primit pentru produsul: {}", pingDTO.getItemId());

        // 1. Salvarea brută a datelor (via serviciul tău existent)
        telemetryService.processPing(pingDTO);

        // 2. Logică de Auto-Healing: Verificăm dacă locația nouă e departe de cea estimată
        try {
            UUID storeUuid = UUID.fromString(pingDTO.getStoreId());
            UUID itemUuid = UUID.fromString(pingDTO.getItemId());

            mapRepository.findByStoreIdAndItemId(storeUuid, itemUuid).ifPresent(map -> {
                if (map.getEstimatedLocPoint() != null) {
                    // Calculăm distanța dintre punctul curent (lng, lat) și cel salvat
                    double distance = calculateHaversineDistance(
                            pingDTO.getLat(), pingDTO.getLng(),
                            map.getEstimatedLocPoint().getY(), map.getEstimatedLocPoint().getX()
                    );

                    // Dacă distanța e mai mare de 15 metri, considerăm că produsul a fost mutat
                    if (distance > 15.0) {
                        log.warn(" Produs mutat detectat ({}m). Item: {}", (int)distance, itemUuid);
                        try {
                            // Trimitem către Worker pentru recalculare rapidă (Async)
                            locationProcessorWorker.recalculateSingleItem(storeUuid, itemUuid);
                        } catch (TaskRejectedException e) {
                            log.error(" Coada de procesare este plină pentru item-ul: {}", itemUuid);
                        }
                    }
                }
            });
        } catch (IllegalArgumentException e) {
            log.error(" ID-uri invalide primite: Store={}, Item={}", pingDTO.getStoreId(), pingDTO.getItemId());
        } catch (Exception e) {
            log.error(" Eroare la procesarea distanței: {}", e.getMessage());
        }

        return ResponseEntity.accepted().body(Map.of("status", "success"));
    }


    @PostMapping("/admin/recalculate-all")
    public ResponseEntity<String> triggerGlobalRecalculation() {
        log.info(" [Admin] Declanșare manuală sincronizare globală.");
        locationProcessorWorker.processAndCalculateCenters();
        return ResponseEntity.ok("Procesul de sincronizare globală a fost pornit.");
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, String>> receiveBatch(@Valid @RequestBody TelemetryBatchDTO batchDTO) {
        log.info("[API] Batch primit cu {} pings", batchDTO.getPings().size());
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