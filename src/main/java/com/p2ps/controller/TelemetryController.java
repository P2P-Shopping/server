package com.p2ps.controller;

import com.p2ps.dto.TelemetryRequest;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/scan")
    public ResponseEntity<?> receiveProductScan(@RequestBody TelemetryRequest request) {
        // [FIX] Validare inițială pentru obiectul request (evită NPE la gettere)
        if (request == null) {
            return ResponseEntity.badRequest().body("Missing request body");
        }

        // 1. Validare câmpuri obligatorii
        if (request.getStoreId() == null || request.getItemId() == null ||
                request.getLat() == null || request.getLon() == null || request.getAccuracy() == null) {
            return ResponseEntity.badRequest().body("Missing required fields: storeId, itemId, lat, lon, or accuracy.");
        }

        // 2. Validare logică a coordonatelor
        if (request.getLat() < -90 || request.getLat() > 90 ||
                request.getLon() < -180 || request.getLon() > 180) {
            return ResponseEntity.badRequest().body("Invalid coordinates.");
        }

        // 3. Validare acuratețe (Filtrare zgomot conform cerințelor: sub 12m este ideal)
        if (request.getAccuracy() < 0 || request.getAccuracy() > 100) {
            return ResponseEntity.badRequest().body("Invalid accuracy range.");
        }

        // 4. Inserare în tabela de log-uri brute (Folosind PostGIS pentru puncte)
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
            log.error("Eroare la inserarea ping-ului brut: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Database error during ping ingestion.");
        }

        // 5. Auto-healing Logic: Verificare deviație
        mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()).ifPresent(map -> {
            if (map.getEstimatedLocPoint() != null) {
                double knownLat = map.getEstimatedLocPoint().getY();
                double knownLon = map.getEstimatedLocPoint().getX();

                double distanceMeters = calculateHaversineDistance(request.getLat(), request.getLon(), knownLat, knownLon);

                // Dacă deviația e mare (>15m), penalizăm scorul de încredere
                if (distanceMeters > 15.0) {
                    log.warn("⚠️ Deviație de {}m detectată pentru item {}", (int)distanceMeters, request.getItemId());

                    double currentScore = (map.getConfidenceScore() == null) ? 0.0 : map.getConfidenceScore();
                    double newScore = Math.max(0.0, currentScore - 0.2);

                    map.setConfidenceScore(newScore);
                    mapRepository.save(map);

                    // Declanșăm recalcularea asincronă (folosind noul executor configurat)
                    locationProcessorWorker.recalculateSingleItem(request.getStoreId(), request.getItemId());
                }
            }
        });

        return ResponseEntity.ok().build();
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