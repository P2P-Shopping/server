package com.p2ps.controller;

import com.p2ps.dto.TelemetryRequest;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final JdbcTemplate jdbcTemplate;
    private final StoreInventoryMapRepository mapRepository;
    private final LocationProcessorWorker locationProcessorWorker;

    public TelemetryController(JdbcTemplate jdbcTemplate, StoreInventoryMapRepository mapRepository, LocationProcessorWorker locationProcessorWorker) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapRepository = mapRepository;
        this.locationProcessorWorker = locationProcessorWorker;
    }

    @PostMapping("/scan")
    public ResponseEntity<Void> receiveProductScan(@RequestBody TelemetryRequest request) {
        String insertSql = "INSERT INTO raw_user_pings (store_id, item_id, location_point, accuracy_m, loc_provider) " +
                "VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 'GPS')";
        jdbcTemplate.update(insertSql, request.getStoreId(), request.getItemId(), request.getLon(), request.getLat(), request.getAccuracy());

        mapRepository.findByStoreIdAndItemId(request.getStoreId(), request.getItemId()).ifPresent(map -> {
            double knownLat = map.getEstimatedLocPoint().getY();
            double knownLon = map.getEstimatedLocPoint().getX();

            double distanceMeters = calculateHaversineDistance(request.getLat(), request.getLon(), knownLat, knownLon);

            if (distanceMeters > 15.0) {
                System.out.println("⚠️ Deviație detectată de " + (int)distanceMeters + "m pentru produsul " + request.getItemId());
                map.setConfidenceScore(Math.max(0.0, map.getConfidenceScore() - 0.2));
                mapRepository.save(map);
                locationProcessorWorker.recalculateSingleItem(request.getStoreId(), request.getItemId());
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
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}