package com.p2ps.catalog.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.dto.ProductSuggestionDTO;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import com.p2ps.util.ProductStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private final ProductCatalogRepository catalogRepository;
    private final UserRepository userRepository;
    private final UserProductHistoryRepository userProductHistoryRepository;

    public CatalogService(
            ProductCatalogRepository catalogRepository,
            UserRepository userRepository,
            UserProductHistoryRepository userProductHistoryRepository) {
        this.catalogRepository = catalogRepository;
        this.userRepository = userRepository;
        this.userProductHistoryRepository = userProductHistoryRepository;
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

        // User history
        if (userEmail != null && !userEmail.isBlank()) {
            Optional<Users> userOpt = userRepository.findByEmail(userEmail);
            if (userOpt.isPresent()) {
                Users user = userOpt.get();
                List<UserProductHistoryRepository.HistoryMatch> historyMatches =
                        userProductHistoryRepository.findMatches(user.getId(), cleanKeyword);

                for (UserProductHistoryRepository.HistoryMatch match : historyMatches) {
                    String bestName = ProductStringUtils.firstNonBlank(
                            match.getItemName(), match.getCatalogSpecificName(), match.getCatalogGenericName()
                    );
                    if (bestName != null && !suggestionsMap.containsKey(bestName)) {
                        suggestionsMap.put(bestName, new ProductSuggestionDTO(
                                bestName,
                                match.getBrand(),
                                match.getCategory(),
                                match.getPrice(),
                                "1"
                        ));
                    }
                }
            }
        }

        // Global catalog
        if (suggestionsMap.size() < 10) {
            List<ProductCatalog> catalogMatches = catalogRepository.searchByKeywordFuzzy(cleanKeyword);

            for (ProductCatalog catalogMatch : catalogMatches) {
                String bestName = ProductStringUtils.firstNonBlank(
                        catalogMatch.getSpecificName(), catalogMatch.getGenericName()
                );
                if (bestName != null && !suggestionsMap.containsKey(bestName)) {
                    suggestionsMap.put(bestName, new ProductSuggestionDTO(
                            bestName,
                            catalogMatch.getBrand(),
                            catalogMatch.getCategory(),
                            catalogMatch.getEstimatedPrice(),
                            "1"
                    ));
                }
                if (suggestionsMap.size() >= 10) break;
            }
        }

        return suggestionsMap.values().stream().limit(10).collect(Collectors.toList());
    }
}
