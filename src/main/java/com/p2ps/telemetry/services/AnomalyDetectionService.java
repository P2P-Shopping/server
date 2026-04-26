package com.p2ps.telemetry.services;

import com.p2ps.telemetry.model.PingStatus;
import com.p2ps.telemetry.model.TelemetryRecord;
import com.p2ps.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    private final TelemetryRepository telemetryRepository;

    private static final double MAX_POSSIBLE_SPEED_M_S = 15.0; // ~54 km/h, which is physically impossible on foot in a store

    // In-memory state to prevent TOCTOU anomalies under concurrent async ingestion
    // and to optimize batch processing. Acts as both a per-key lock and a cache.
    private final ConcurrentHashMap<String, TelemetryRecord> latestValidPings = new ConcurrentHashMap<>();

    public void evaluateAndSetStatus(TelemetryRecord newRecord) {
        String stateKey = newRecord.getDeviceId() + ":" + newRecord.getStoreId() + ":" + newRecord.getItemId();

        // compute() guarantees atomicity per key, acting as a lightweight lock
        latestValidPings.compute(stateKey, (key, lastPing) -> {
            applyAccuracyCheck(newRecord);
            
            TelemetryRecord referencePing = resolveReferencePing(newRecord, lastPing);
            
            if (referencePing != null) {
                if (isImpossibleSpeed(newRecord, referencePing)) {
                    newRecord.setStatus(PingStatus.REJECTED);
                    return referencePing; // Do not update the state with a rejected ping
                }
                
                if (isOutOfOrder(newRecord, referencePing)) {
                    ensureStatusSet(newRecord);
                    return referencePing; // Do not update state with an out-of-order ping
                }
            }

            ensureStatusSet(newRecord);
            // The valid incoming ping becomes the new baseline state for this key
            return newRecord;
        });
    }

    private void applyAccuracyCheck(TelemetryRecord pingRecord) {
        if (pingRecord.getAccuracyMeters() != null && pingRecord.getAccuracyMeters() > 30) {
            pingRecord.setStatus(PingStatus.DEGRADED);
            log.info("[ANOMALY] Ping marked as DEGRADED due to low accuracy ({}m)", pingRecord.getAccuracyMeters());
        }
    }

    private TelemetryRecord resolveReferencePing(TelemetryRecord newRecord, TelemetryRecord lastPing) {
        if (lastPing != null) {
            return lastPing;
        }
        return telemetryRepository.findTopByDeviceIdAndStoreIdAndItemIdOrderByTimestampDesc(
                newRecord.getDeviceId(), newRecord.getStoreId(), newRecord.getItemId()).orElse(null);
    }

    private boolean isImpossibleSpeed(TelemetryRecord newRecord, TelemetryRecord lastPing) {
        if (newRecord.getTimestamp() == null || lastPing.getTimestamp() == null) {
            return false;
        }

        double timeDiffSeconds = (newRecord.getTimestamp() - lastPing.getTimestamp()) / 1000.0;
        
        // Out-of-order pings are handled by isOutOfOrder()
        if (timeDiffSeconds < 0) {
            return false;
        }

        if (newRecord.getLat() == null || newRecord.getLng() == null ||
            lastPing.getLat() == null || lastPing.getLng() == null) {
            return false;
        }

        double distanceMeters = calculateDistance(
                lastPing.getLat(), lastPing.getLng(),
                newRecord.getLat(), newRecord.getLng()
        );

        if (timeDiffSeconds == 0) {
            if (distanceMeters > 5) {
                log.warn("[ANOMALY] Ping marked as REJECTED due to jumping location at the exact same timestamp");
                return true;
            }
            return false;
        }

        double speed = distanceMeters / timeDiffSeconds;
        if (speed > MAX_POSSIBLE_SPEED_M_S) {
            log.warn("[ANOMALY] Ping marked as REJECTED due to impossible speed ({} m/s)", speed);
            return true;
        }

        return false;
    }

    private boolean isOutOfOrder(TelemetryRecord newRecord, TelemetryRecord lastPing) {
        if (newRecord.getTimestamp() == null || lastPing.getTimestamp() == null) {
            return false;
        }
        return newRecord.getTimestamp() < lastPing.getTimestamp();
    }

    private void ensureStatusSet(TelemetryRecord pingRecord) {
        if (pingRecord.getStatus() == null) {
            pingRecord.setStatus(PingStatus.ACCEPTED);
        }
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
