package com.p2ps.controller;

import com.p2ps.dto.TelemetryRequest;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

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
        // 1. Validare câmpuri obligatorii (pentru a evita null-uri sau valori 0.0 implicite)
        if (request.getStoreId() == null || request.getItemId() == null ||
                request.getLat() == null || request.getLon() == null || request.getAccuracy() == null) {
            return ResponseEntity.badRequest().body("Missing required fields: storeId, itemId, lat, lon, or accuracy.");
        }

        // 2. Validare logică a coordonatelor (Geofencing de bază)
        if (request.getLat() < -90 || request.getLat() > 90 ||
                request.getLon() < -180 || request.getLon() > 180) {
            return ResponseEntity.badRequest().body("Invalid coordinates: Latitude [-90, 90], Longitude [-180, 180].");
        }

        // 3. Validare acuratețe (Filtrare zgomot)
        if (request.getAccuracy() < 0 || request.getAccuracy() > 100) {
            return ResponseEntity.badRequest().body("Invalid accuracy: Must be between 0 and 100 meters.");
        }

        // 4. Inserare în tabela de log-uri brute (raw_user_pings)
        String insertSql = "INSERT INTO raw_user_pings (store_id, item_id, location_point, accuracy_m, loc_provider) " +
                "VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 'GPS')";

        jdbcTemplate.update(insertSql,
                request.getStoreId(),
                request.getItemId(),
                request.getLon(),
                request.getLat(),
                request.getAccuracy()
        );

        // 5. Verificare deviație față de locația cunoscută (Self-Healing Logic)
        mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()).ifPresent(map -> {
            // Extragem coordonatele calculate anterior
            double knownLat = map.getEstimatedLocPoint().getY();
            double knownLon = map.getEstimatedLocPoint().getX();

            double distanceMeters = calculateHaversineDistance(request.getLat(), request.getLon(), knownLat, knownLon);

            // Dacă noul ping este la mai mult de 15m de ce știam, penalizăm scorul
            if (distanceMeters > 15.0) {
                System.out.println("⚠️ Deviație detectată de " + (int)distanceMeters + "m pentru produsul " + request.getItemId());

                // FIX: Citire null-safe a scorului curent
                double currentScore = (map.getConfidenceScore() == null) ? 0.0 : map.getConfidenceScore();

                // Calculăm noul scor prin decrementare, asigurându-ne că nu coborâm sub 0.0
                double newScore = Math.max(0.0, currentScore - 0.2);

                map.setConfidenceScore(newScore);
                mapRepository.save(map);

                // Declanșăm recalcularea asincronă doar pentru acest item
                locationProcessorWorker.recalculateSingleItem(request.getStoreId(), request.getItemId());
            }
        });

        return ResponseEntity.ok().build();
    }

    /**
     * Calculează distanța aeriană între două puncte geografice (în metri).
     */
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