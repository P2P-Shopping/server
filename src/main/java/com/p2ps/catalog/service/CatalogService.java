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
        
        return catalogRepository.findBySpecificNameAndBrand(specificName, brand)
                .map(existingProduct -> {
                    existingProduct.setPurchaseCount(existingProduct.getPurchaseCount() + 1);
                    // Actualizam pretul estimativ sau categoria doar daca sunt furnizate noi informatii care imbunatatesc datele
                    if (price != null) {
                         // O abordare simpla ar fi sa inlocuim pretul, sau s-ar putea calcula un "rolling average"
                        existingProduct.setEstimatedPrice(price); 
                    }
                    if (category != null && !category.isBlank()) {
                        existingProduct.setCategory(category);
                    }
                    return catalogRepository.save(existingProduct);
                })
                .orElseGet(() -> {
                    ProductCatalog newProduct = new ProductCatalog();
                    newProduct.setGenericName(genericName != null ? genericName : "Unknown");
                    newProduct.setSpecificName(specificName);
                    newProduct.setBrand(brand);
                    newProduct.setCategory(category);
                    newProduct.setEstimatedPrice(price);
                    newProduct.setPurchaseCount(1);
                    return catalogRepository.save(newProduct);
                });
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