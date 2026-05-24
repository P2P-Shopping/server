package com.p2ps.catalog.service;

import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.dto.ProductSuggestionDTO;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import com.p2ps.util.ProductStringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CatalogService {

    private final ProductCatalogRepository catalogRepository;
    private final UserRepository userRepository;
    private final UserProductHistoryRepository userProductHistoryRepository;
    private final StorePriceService storePriceService;
    private final CatalogService self;

    public CatalogService(
            ProductCatalogRepository catalogRepository,
            UserRepository userRepository,
            UserProductHistoryRepository userProductHistoryRepository,
            StorePriceService storePriceService,
            @Lazy CatalogService self) {
        this.catalogRepository = catalogRepository;
        this.userRepository = userRepository;
        this.userProductHistoryRepository = userProductHistoryRepository;
        this.storePriceService = storePriceService;
        this.self = self;
    }

    @Transactional
    public ProductCatalog recordPurchase(String genericName, String specificName, String brand, String category, BigDecimal price) {
        return self.recordPurchase(genericName, specificName, brand, category, price, (UUID) null);
    }

    @Transactional
    public ProductCatalog recordPurchase(String genericName, String specificName, String brand, String category, BigDecimal price, UUID storeId) {
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
        ProductCatalog recordedProduct = catalogRepository.findBySpecificNameAndBrand(specificName, brand)
                .orElseThrow(() -> new IllegalStateException("Product should have been created by upsert but was not found."));
        storePriceService.recordStorePrice(recordedProduct, storeId, price);
        return recordedProduct;
    }

    @Transactional
    public ProductCatalog recordPurchase(String genericName, String specificName, String brand, String category, BigDecimal price, String ignoredStoreName) {
        return self.recordPurchase(genericName, specificName, brand, category, price, (UUID) null);
    }

    @Transactional(readOnly = true)
    public List<ProductCatalog> getTopPopularProducts() {
        return catalogRepository.findTop50ByOrderByPurchaseCountDesc();
    }
    
    @Transactional(readOnly = true)
    public List<UUID> getBestStoresForCatalogProduct(UUID catalogId) {
        return catalogRepository.findBestStoresForCatalogProduct(catalogId);
    }

    @Transactional(readOnly = true)
    public List<ProductCatalog> searchProductsByName(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of(); // returnează listă goală dacă AI-ul trimite null
        }
        return catalogRepository.searchByKeyword(keyword.trim()).stream()
                .limit(10)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductSuggestionDTO> suggestProducts(String keyword, String userEmail) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        String cleanKeyword = keyword.trim();
        Map<String, ProductSuggestionDTO> suggestionsMap = new LinkedHashMap<>();

        addHistorySuggestions(userEmail, cleanKeyword, suggestionsMap);
        addCatalogSuggestions(cleanKeyword, suggestionsMap);

        return suggestionsMap.values().stream().limit(10).toList();
    }

    private void addHistorySuggestions(String userEmail, String keyword, Map<String, ProductSuggestionDTO> suggestionsMap) {
        if (userEmail == null || userEmail.isBlank()) return;

        userRepository.findByEmail(userEmail).ifPresent(user -> {
            List<UserProductHistoryRepository.HistoryMatch> historyMatches =
                    userProductHistoryRepository.findMatches(user.getId(), keyword);

            for (UserProductHistoryRepository.HistoryMatch match : historyMatches) {
                String bestName = ProductStringUtils.firstNonBlank(
                        match.getItemName(), match.getCatalogSpecificName(), match.getCatalogGenericName()
                );
                if (bestName != null) {
                    suggestionsMap.computeIfAbsent(bestName, k -> new ProductSuggestionDTO(
                            k,
                            match.getBrand(),
                            match.getCategory(),
                            match.getPrice(),
                            normalizeQuantity(match.getCatalogDefaultQuantity())
                    ));
                }
            }
        });
    }

    private void addCatalogSuggestions(String keyword, Map<String, ProductSuggestionDTO> suggestionsMap) {
        if (suggestionsMap.size() >= 10) return;

        List<ProductCatalog> catalogMatches = catalogRepository.searchByKeyword(keyword);

        for (ProductCatalog catalogMatch : catalogMatches) {
            String bestName = ProductStringUtils.firstNonBlank(
                    catalogMatch.getSpecificName(), catalogMatch.getGenericName()
            );
            if (bestName != null) {
                suggestionsMap.computeIfAbsent(bestName, k -> new ProductSuggestionDTO(
                        k,
                        catalogMatch.getBrand(),
                        catalogMatch.getCategory(),
                        catalogMatch.getEstimatedPrice(),
                        normalizeQuantity(catalogMatch.getDefaultQuantity())
                ));
            }
            if (suggestionsMap.size() >= 10) break;
        }
    }

    private String normalizeQuantity(String quantity) {
        if (quantity == null || quantity.trim().isEmpty()) {
            return "1 buc";
        }
        return quantity.trim();
    }
}
