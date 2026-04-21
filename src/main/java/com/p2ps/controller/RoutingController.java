package com.p2ps.controller;

import com.p2ps.dto.ItemLocationDTO;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.service.RoutingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private static final Duration RECALCULATION_COOLDOWN = Duration.ofMinutes(1);

    private final Cache<String, Instant> recalculationGuard = Caffeine.newBuilder()
            .expireAfterWrite(RECALCULATION_COOLDOWN)
            .maximumSize(10_000)
            .build();

    private final RoutingService routingService;
    private final StoreInventoryMapRepository inventoryMapRepository;
    private final LocationProcessorWorker locationProcessorWorker;

    public RoutingController(RoutingService routingService, StoreInventoryMapRepository inventoryMapRepository,
                             LocationProcessorWorker locationProcessorWorker) {
        this.routingService = routingService;
        this.inventoryMapRepository = inventoryMapRepository;
        this.locationProcessorWorker = locationProcessorWorker;
    }

    @PostMapping("/calculate")
    public RoutingResponse calculateRoute(@RequestBody RoutingRequest request) {
        return routingService.calculateOptimalRoute(request);
    }

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
                            map.getEstimatedLocPoint().getCoordinate().y, // Reparatie JTS Point
                            map.getEstimatedLocPoint().getCoordinate().x, // Reparatie JTS Point
                            isLowConfidence,
                            confidenceScore
                    );
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean shouldTriggerRecalculation(UUID storeId, UUID itemId, LocalDateTime lastUpdated) {
        String guardKey = storeId + ":" + itemId;
        LocalDateTime cutoff = LocalDateTime.now().minus(RECALCULATION_COOLDOWN);
        AtomicBoolean shouldTrigger = new AtomicBoolean(false);

        recalculationGuard.asMap().compute(guardKey, (key, previous) -> {
            if (previous != null) {
                return previous;
            }

            if (lastUpdated != null && !lastUpdated.isBefore(cutoff)) {
                return null;
            }

            shouldTrigger.set(true);
            return Instant.now();
        });

        return shouldTrigger.get();
    }
}
