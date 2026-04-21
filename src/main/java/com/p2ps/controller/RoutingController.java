package com.p2ps.controller;

import com.p2ps.dto.ItemLocationDTO;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.LocationProcessorWorker;
import com.p2ps.service.RoutingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private static final double LOW_CONFIDENCE_THRESHOLD = 0.4d;
    private static final int MIN_PINGS_FOR_CONFIDENCE = 5;

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
                    boolean isLowConfidence = confidenceScore < LOW_CONFIDENCE_THRESHOLD || pingCount < MIN_PINGS_FOR_CONFIDENCE;

                    if (isLowConfidence) {
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
}
