package com.p2ps.proximity.service;

import com.p2ps.proximity.dto.LocationPingDTO;
import com.p2ps.proximity.model.ActiveListLocation;
import com.p2ps.proximity.repository.ActiveListLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Core proximity matching service.
 *
 * Flow:
 *   1. Receive a background location ping from the Android app.
 *   2. Query MongoDB for active list locations within the configured radius.
 *   3. For each match, check Redis for a debounce key to prevent duplicate notifications.
 *   4. If no debounce key exists, dispatch an FCM notification and set the debounce key.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProximityMatchingService {

    private final ActiveListLocationRepository activeListLocationRepository;
    private final StringRedisTemplate redisTemplate;
    private final FcmService fcmService;

    @Value("${proximity.radius.meters:500}")
    private double radiusMeters;

    @Value("${proximity.debounce.hours:24}")
    private long debounceHours;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    private static final String DEBOUNCE_KEY_PREFIX = "proximity:notified:";

    /**
     * Processes a background location ping asynchronously.
     * Runs on the telemetry executor thread pool to avoid blocking the HTTP thread.
     */
    @Async("telemetryExecutor")
    public void processLocationPing(LocationPingDTO pingDTO) {
        log.info("[PROXIMITY] Processing location ping for device: {}", pingDTO.getDeviceId());

        // MongoDB $near requires longitude first, then latitude
        Point userLocation = new Point(pingDTO.getLng(), pingDTO.getLat());
        Distance radius = new Distance(radiusMeters / 1000.0, Metrics.KILOMETERS);

        List<ActiveListLocation> nearbyLists = activeListLocationRepository
                .findByCoordinatesNear(userLocation, radius);

        if (nearbyLists.isEmpty()) {
            log.debug("[PROXIMITY] No active lists found near device: {}", pingDTO.getDeviceId());
            return;
        }

        log.info("[PROXIMITY] Found {} active list(s) near device: {}", nearbyLists.size(), pingDTO.getDeviceId());

        for (ActiveListLocation location : nearbyLists) {
            String debounceKey = buildDebounceKey(pingDTO.getDeviceId(), location.getListId());
            sendNotificationAndDebounce(pingDTO, location, debounceKey);
        }
    }


    private void sendNotificationAndDebounce(LocationPingDTO pingDTO,
                                             ActiveListLocation location,
                                             String debounceKey) {
        String deepLink = appBaseUrl + "/list/" + location.getListId();

        // Atomic SET NX EX — eliminates TOCTOU race condition
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(debounceKey, "1", debounceHours, TimeUnit.HOURS);

        if (!Boolean.TRUE.equals(isNew)) {
            log.debug("[PROXIMITY] Skipping notification for device: {}, list: {} — debounce active",
                    pingDTO.getDeviceId(), location.getListId());
            return;
        }

        fcmService.sendProximityAlert(
                pingDTO.getFcmToken(),
                "Item nearby!",
                "A shopping list item is available near your current location.",
                deepLink
        );

        log.info("[PROXIMITY] Notification dispatched and debounce set for device: {}, list: {}",
                pingDTO.getDeviceId(), location.getListId());
    }

    private String buildDebounceKey(String deviceId, String listId) {
        return DEBOUNCE_KEY_PREFIX + deviceId + ":" + listId;
    }
}