package com.p2ps.catalog.controller;

import com.p2ps.catalog.dto.RecordPurchaseRequest;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // 1. Endpoint pentru a vedea topul produselor (folosit de AI)
    @GetMapping("/top")
    public ResponseEntity<List<ProductCatalog>> getTopProducts() {
        return ResponseEntity.ok(catalogService.getTopPopularProducts());
    }

    // 2. Endpoint pentru a simula scanarea unui bon și "recoltarea" unui produs
    @PostMapping("/record")
    public ResponseEntity<ProductCatalog> recordPurchase(@Valid @RequestBody RecordPurchaseRequest request) {
        
        ProductCatalog recordedProduct = catalogService.recordPurchase(
                request.getGenericName(),
                request.getSpecificName(),
                request.getBrand(),
                request.getCategory(),
                request.getPrice()
        );

        return ResponseEntity.ok(recordedProduct);
    }
    
    // 3. Endpoint pentru a vedea ce magazine (store_id) au acest produs
    @GetMapping("/{catalogId}/best-stores")
    public ResponseEntity<List<UUID>> getBestStores(@PathVariable UUID catalogId) {
        return ResponseEntity.ok(catalogService.getBestStoresForCatalogProduct(catalogId));
    }
}