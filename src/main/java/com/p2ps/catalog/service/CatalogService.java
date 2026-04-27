package com.p2ps.catalog.service;

import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CatalogService {

    private final ProductCatalogRepository catalogRepository;

    public CatalogService(ProductCatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    @Transactional
    public ProductCatalog recordPurchase(String genericName, String specificName, String brand, String category, BigDecimal price) {
        if (specificName == null || specificName.isBlank()) {
            return null; // Cannot catalog without a specific name
        }
        
        // Delegam toata logica de find-or-create direct bazei de date, care o va executa atomic
        catalogRepository.upsertProduct(
            genericName != null ? genericName : "Unknown",
            specificName,
            brand,
            category,
            price
        );
        
        // Dupa ce operatia atomica s-a incheiat, cautam produsul pentru a-l returna controller-ului
        return catalogRepository.findBySpecificNameAndBrand(specificName, brand)
                .orElseThrow(() -> new IllegalStateException("Product should have been created by upsert but was not found."));
    }

    @Transactional(readOnly = true)
    public List<ProductCatalog> getTopPopularProducts() {
        return catalogRepository.findTop50ByOrderByPurchaseCountDesc();
    }
    
    @Transactional(readOnly = true)
    public List<UUID> getBestStoresForCatalogProduct(UUID catalogId) {
        return catalogRepository.findBestStoresForCatalogProduct(catalogId);
    }
}