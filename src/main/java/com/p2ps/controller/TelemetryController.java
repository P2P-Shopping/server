package com.p2ps.controller;

import com.p2ps.dto.TelemetryRequest;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);

    private final JdbcTemplate jdbcTemplate;
    private final StoreInventoryMapRepository mapRepository;
    private final LocationProcessorWorker locationProcessorWorker;

    public TelemetryController(JdbcTemplate jdbcTemplate,
                               StoreInventoryMapRepository mapRepository,
                               LocationProcessorWorker locationProcessorWorker) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapRepository = mapRepository;
        this.locationProcessorWorker = locationProcessorWorker;
    }

    /**
     * Primește un ping de locație de la utilizator, îl salvează în baza de date
     * și declanșează recalcularea centrului produsului dacă este necesar.
     */
    @PostMapping("/scan")
    public ResponseEntity<?> receiveProductScan(@Valid @RequestBody TelemetryRequest request) {
        // Validarea (null check, range check) se face automat via @Valid și adnotările din DTO

        // 1. Inserare în tabela de log-uri brute (raw_user_pings)
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

        // 2. Logică de auto-healing și recalculare asincronă
        mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()).ifPresent(map -> {
            if (map.getEstimatedLocPoint() != null) {
                double distance = calculateHaversineDistance(
                        request.getLat(), request.getLon(),
                        map.getEstimatedLocPoint().getY(), map.getEstimatedLocPoint().getX()
                );

                // Dacă noul ping indică o mutare a produsului (>15m)
                if (distance > 15.0) {
                    log.warn("⚠️ Produs mutat detectat ({}m). Declanșăm recalcularea pentru: {}", (int)distance, request.getItemId());

                    try {
                        // Pornim recalcularea asincronă (pe un alt fir de execuție)
                        locationProcessorWorker.recalculateSingleItem(request.getStoreId(), request.getItemId());
                    } catch (TaskRejectedException e) {
                        // Dacă pool-ul de thread-uri este plin (AbortPolicy), logăm și informăm sistemul
                        log.error("🚫 Coada de execuție asincronă este plină. Recalcularea pentru {} a fost amânată.", request.getItemId());
                    }
                }
            }
        });

        return ResponseEntity.ok().build();
    }

    /**
     * Endpoint administrativ pentru declanșarea manuală a sincronizării globale.
     * [Fix] Înlocuiește vechiul @Scheduled care a fost eliminat pentru stabilitate.
     */
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