package com.p2ps.controller;

import com.p2ps.dto.ItemLocationDTO;
import com.p2ps.repository.StoreInventoryMapRepository;
import com.p2ps.service.RoutingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private final RoutingService routingService;
    private final StoreInventoryMapRepository inventoryMapRepository;

    public RoutingController(RoutingService routingService, StoreInventoryMapRepository inventoryMapRepository) {
        this.routingService = routingService;
        this.inventoryMapRepository = inventoryMapRepository;
    }

    @PostMapping("/calculate")
    public RoutingResponse calculateRoute(@RequestBody RoutingRequest request) {
        return routingService.calculateOptimalRoute(request);
    }

    @GetMapping("/location")
    public ResponseEntity<ItemLocationDTO> getItemLocation(@RequestParam UUID storeId, @RequestParam UUID itemId) {
        return inventoryMapRepository.findByStoreIdAndItemId(storeId, itemId)
                .map(map -> {
                    boolean isLowConfidence = map.getConfidenceScore() < 0.4 || map.getPingCount() < 5;
                    ItemLocationDTO dto = new ItemLocationDTO(
                            map.getEstimatedLocPoint().getCoordinate().y, // Reparatie JTS Point
                            map.getEstimatedLocPoint().getCoordinate().x, // Reparatie JTS Point
                            isLowConfidence,
                            map.getConfidenceScore()
                    );
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}