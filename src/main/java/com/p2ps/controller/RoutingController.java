package com.p2ps.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.dto.ItemLocationDTO;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.service.MacroRoutingService;
import com.p2ps.service.RoutingAsyncService;
import com.p2ps.service.RoutingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private static final Logger logger = LoggerFactory.getLogger(RoutingController.class);

    private static final String DEFAULT_COOLDOWN_STR = "PT1M";
    private static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(1);
    private static final int DEFAULT_GUARD_MAX_SIZE = 10000;

    @Value("${routing.recalculation.cooldown:" + DEFAULT_COOLDOWN_STR + "}")
    private Duration recalculationCooldown;

    @Value("${routing.recalculation.guard.max-size:" + DEFAULT_GUARD_MAX_SIZE + "}")
    private int recalculationGuardMaxSize;

    private Cache<String, Instant> recalculationGuard;

    private final RoutingService routingService;
    private final MacroRoutingService macroRoutingService;
    private final StoreInventoryMapRepository inventoryMapRepository;
    private final LocationProcessorWorker locationProcessorWorker;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RoutingController(
            RoutingService routingService,
            MacroRoutingService macroRoutingService,
            StoreInventoryMapRepository inventoryMapRepository,
            LocationProcessorWorker locationProcessorWorker,
            StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.routingService = routingService;
        this.macroRoutingService = macroRoutingService;
        this.inventoryMapRepository = inventoryMapRepository;
        this.locationProcessorWorker = locationProcessorWorker;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        this.recalculationGuard = Caffeine.newBuilder()
                .expireAfterWrite(this.recalculationCooldown != null ? this.recalculationCooldown : DEFAULT_COOLDOWN)
                .maximumSize(this.recalculationGuardMaxSize)
                .build();
    }

    // -------------------------------------------------------------------------
    // Existing endpoint (unchanged contract, now supports lazy via request body)
    // -------------------------------------------------------------------------

    @PostMapping("/calculate")
    public RoutingResponse calculateRoute(@RequestBody RoutingRequest request) {
        return routingService.calculateOptimalRoute(request);
    }

    // -------------------------------------------------------------------------
    // BE 3.1 — Lazy Routing: retrieve completed background route
    // -------------------------------------------------------------------------

    /**
     * GET /api/routing/full/{routeId}
     * 200 — full route ready | 202 — still computing | 404 — unknown | 500 — error
     */
    @GetMapping("/full/{routeId}")
    public ResponseEntity<RoutingResponse> getFullRoute(@PathVariable String routeId) {
        String key = RoutingAsyncService.ROUTE_KEY_PREFIX + routeId;
        String json = redis.opsForValue().get(key);

        if (json == null || json.isBlank()) {
            String pendingKey = RoutingAsyncService.PENDING_KEY_PREFIX + routeId;
            Boolean isPending = redis.hasKey(pendingKey);

            if (Boolean.TRUE.equals(isPending)) {
                logger.debug("Route optimization still in progress: routeId={}", routeId);
                return ResponseEntity.accepted().build();
            } else {
                logger.debug("Route not found and not pending in Redis: routeId={}", routeId);
                return ResponseEntity.notFound().build();
            }
        }

        try {
            RoutingResponse response = objectMapper.readValue(json, RoutingResponse.class);
            return ResponseEntity.ok(response);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse route JSON from Redis: routeId={}", routeId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // BE 3.2 — Macro-Routing: walking + driving to store entrance
    // -------------------------------------------------------------------------

    /**
     * GET /api/routing/macro?userLat=...&userLng=...&storeId=...
     *
     * Returns walking and driving estimates from the user's location
     * to the store entrance (ST_Centroid of boundary_polygon).
     */
    @GetMapping("/macro")
    public ResponseEntity<MacroRoutingResponse> getMacroEstimates(
            @RequestParam double userLat,
            @RequestParam double userLng,
            @RequestParam String storeId) {

        MacroRoutingResponse response = macroRoutingService.getEstimates(userLat, userLng, storeId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Existing endpoint (unchanged)
    // -------------------------------------------------------------------------

    @GetMapping("/location")
    public ResponseEntity<ItemLocationDTO> getItemLocation(@RequestParam UUID storeId, @RequestParam UUID itemId) {
        return inventoryMapRepository.findByStoreIdAndItemId(storeId, itemId)
                .map(map -> {
                    double confidenceScore = map.getConfidenceScore() == null ? 0.0d : map.getConfidenceScore();
                    int pingCount = map.getPingCount() == null ? 0 : map.getPingCount();
                    boolean isLowConfidence = locationProcessorWorker.isLowConfidence(confidenceScore, pingCount);

                    if (isLowConfidence && shouldTriggerRecalculation(storeId, itemId, map.getLastUpdated())) {
                        locationProcessorWorker.recalculateSingleItem(storeId, itemId);
                    }

                    ItemLocationDTO dto = new ItemLocationDTO(
                            map.getEstimatedLocPoint().getCoordinate().y,
                            map.getEstimatedLocPoint().getCoordinate().x,
                            isLowConfidence,
                            confidenceScore
                    );
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean shouldTriggerRecalculation(UUID storeId, UUID itemId, LocalDateTime lastUpdated) {
        String guardKey = storeId + ":" + itemId;
        LocalDateTime cutoff = LocalDateTime.now().minus(recalculationCooldown);
        AtomicBoolean shouldTrigger = new AtomicBoolean(false);

        recalculationGuard.asMap().compute(guardKey, (key, previous) -> {
            if (previous != null) return previous;
            if (lastUpdated != null && !lastUpdated.isBefore(cutoff)) return null;
            shouldTrigger.set(true);
            return Instant.now();
        });

        return shouldTrigger.get();
    }
}