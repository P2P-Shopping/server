package com.p2ps.telemetry.services;

import com.p2ps.telemetry.model.PingStatus;
import com.p2ps.telemetry.model.TelemetryRecord;
import com.p2ps.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    private final TelemetryRepository telemetryRepository;

    private static final double MAX_POSSIBLE_SPEED_M_S = 15.0; // ~54 km/h, which is physically impossible on foot in a store

    public void evaluateAndSetStatus(TelemetryRecord newRecord) {
        // 1. Check for bad accuracy
        if (newRecord.getAccuracyMeters() != null && newRecord.getAccuracyMeters() > 30) {
            newRecord.setStatus(PingStatus.DEGRADED);
            log.info("[ANOMALY] Ping marked as DEGRADED due to low accuracy ({}m)", newRecord.getAccuracyMeters());
            return;
        }

        // 2. Check for physically impossible speed
        Optional<TelemetryRecord> lastPingOpt = telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(
                newRecord.getDeviceId(), newRecord.getStoreId(), newRecord.getItemId());

        if (lastPingOpt.isPresent()) {
            TelemetryRecord lastPing = lastPingOpt.get();
            
            // Calculate time difference in seconds
            double timeDiffSeconds = (newRecord.getTimestamp() - lastPing.getTimestamp()) / 1000.0;
            
            // Ensure time moves forward (if a ping comes out of order we might not be able to compute speed simply, but we assume chronological mostly)
            if (timeDiffSeconds > 0) {
                double distanceMeters = calculateDistance(
                        lastPing.getLat(), lastPing.getLng(),
                        newRecord.getLat(), newRecord.getLng()
                );
                
                double speed = distanceMeters / timeDiffSeconds;
                
                if (speed > MAX_POSSIBLE_SPEED_M_S) {
                    newRecord.setStatus(PingStatus.REJECTED);
                    log.warn("[ANOMALY] Ping marked as REJECTED due to impossible speed ({} m/s)", speed);
                    return;
                }
            } else if (timeDiffSeconds == 0) {
                // two pings at the exact same millisecond with different coordinates is basically impossible
                 double distanceMeters = calculateDistance(
                        lastPing.getLat(), lastPing.getLng(),
                        newRecord.getLat(), newRecord.getLng()
                );
                if (distanceMeters > 5) {
                    newRecord.setStatus(PingStatus.REJECTED);
                    log.warn("[ANOMALY] Ping marked as REJECTED due to jumping location at the exact same timestamp");
                    return;
                }
            }
        }

        // If no anomaly detected, ping is accepted
        newRecord.setStatus(PingStatus.ACCEPTED);
    }

    /**
     * Calculates distance in meters between two coordinates using the Haversine formula.
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of the earth in meters
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
                
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}
