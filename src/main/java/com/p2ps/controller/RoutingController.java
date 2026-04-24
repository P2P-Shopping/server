package com.p2ps.controller;

import com.p2ps.dto.ItemLocationDTO;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.service.RoutingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private final Duration recalculationCooldown;
    private final Cache<String, Instant> recalculationGuard;

    private final RoutingService routingService;
    private final StoreInventoryMapRepository inventoryMapRepository;
    private final LocationProcessorWorker locationProcessorWorker;

    public RoutingController(RoutingService routingService, StoreInventoryMapRepository inventoryMapRepository,
                             LocationProcessorWorker locationProcessorWorker,
                             @Value("${routing.recalculation.cooldown:PT1M}") Duration recalculationCooldown,
                             @Value("${routing.recalculation.guard.max-size:10000}") long recalculationGuardMaxSize) {
        this.routingService = routingService;
        this.inventoryMapRepository = inventoryMapRepository;
        this.locationProcessorWorker = locationProcessorWorker;
        this.recalculationCooldown = recalculationCooldown;
        this.recalculationGuard = Caffeine.newBuilder()
                .expireAfterWrite(recalculationCooldown)
                .maximumSize(recalculationGuardMaxSize)
                .build();
    }

    @PostMapping("/calculate")
    public RoutingResponse calculateRoute(@RequestBody RoutingRequest request) {
        return routingService.calculateOptimalRoute(request);
    }

    @GetMapping("/location")
    public ResponseEntity<ItemLocationDTO> getItemLocation(@RequestParam UUID storeId, @RequestParam UUID itemId) {
        var mapOptional = inventoryMapRepository.findByStoreIdAndItemId(storeId, itemId);
        if (mapOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var map = mapOptional.get();
        double confidenceScore = map.getConfidenceScore() == null ? 0.0d : map.getConfidenceScore();
        int pingCount = map.getPingCount() == null ? 0 : map.getPingCount();
        boolean isLowConfidence = locationProcessorWorker.isLowConfidence(confidenceScore, pingCount);

        if (isLowConfidence && shouldTriggerRecalculation(storeId, itemId, map.getLastUpdated())) {
            try {
                locationProcessorWorker.recalculateSingleItem(storeId, itemId)
                        .whenComplete((ignored, ex) -> {
                            if (ex != null) {
                                log.warn("Rapid recalculation completed exceptionally for storeId={} itemId={}", storeId, itemId, ex);
                            }
                        });
            } catch (RuntimeException ex) {
                log.warn("Failed to enqueue location recalculation for storeId={} itemId={}", storeId, itemId, ex);
            }
        }

        if (map.getEstimatedLocPoint() == null || map.getEstimatedLocPoint().getCoordinate() == null) {
            log.debug("Location is currently unavailable for storeId={} itemId={}", storeId, itemId);
            return ResponseEntity.noContent().build();
        }

        ItemLocationDTO dto = new ItemLocationDTO(
                map.getEstimatedLocPoint().getCoordinate().y,
                map.getEstimatedLocPoint().getCoordinate().x,
                isLowConfidence,
                confidenceScore
        );
        return ResponseEntity.ok(dto);
    }

    private boolean shouldTriggerRecalculation(UUID storeId, UUID itemId, LocalDateTime lastUpdated) {
        String guardKey = storeId + ":" + itemId;
        LocalDateTime cutoff = LocalDateTime.now().minus(recalculationCooldown);
        AtomicBoolean shouldTrigger = new AtomicBoolean(false);

        recalculationGuard.asMap().computeIfAbsent(guardKey, key -> {
            if (lastUpdated != null && !lastUpdated.isBefore(cutoff)) {
                return null;
            }

            shouldTrigger.set(true);
            return Instant.now();
        });

        return shouldTrigger.get();
    }
}
