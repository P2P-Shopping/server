package com.p2ps.catalog.service;

import com.p2ps.ai.service.CatalogItemStandardizationService;
import com.p2ps.ai.service.CatalogStandardizationResult;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class GlobalCatalogPopulationService {

    private final UserProductHistoryRepository userProductHistoryRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final CatalogItemStandardizationService catalogItemStandardizationService;
    private final TransactionTemplate transactionTemplate;
    private final StorePriceService storePriceService;

    public GlobalCatalogPopulationService(
            UserProductHistoryRepository userProductHistoryRepository,
            ProductCatalogRepository productCatalogRepository,
            CatalogItemStandardizationService catalogItemStandardizationService,
            TransactionTemplate transactionTemplate,
            StorePriceService storePriceService) {
        this.userProductHistoryRepository = userProductHistoryRepository;
        this.productCatalogRepository = productCatalogRepository;
        this.catalogItemStandardizationService = catalogItemStandardizationService;
        this.transactionTemplate = transactionTemplate;
        this.storePriceService = storePriceService;
    }

    public int populateFromPopularUnknownProducts(int minDistinctUsers) {
        List<UserProductHistoryRepository.PopularUnknownProduct> candidates =
                userProductHistoryRepository.findPopularUnknownProducts(minDistinctUsers);

        int processedCount = 0;
        for (UserProductHistoryRepository.PopularUnknownProduct candidate : candidates) {
            try {
                // Pasăm întregul obiect 'candidate', nu doar numele
                Boolean linked = transactionTemplate.execute(status -> processCandidate(candidate));
                if (Boolean.TRUE.equals(linked)) {
                    processedCount++;
                }
            } catch (Exception exception) {
                log.warn("[GLOBAL_CATALOG_POPULATION] Failed to process candidate '{}': {}",
                        candidate.getCustomName(), exception.getMessage());
            }
        }
        return processedCount;
    }

    private boolean processCandidate(UserProductHistoryRepository.PopularUnknownProduct candidate) {
        String normalizedRawName = normalizeRequired(candidate.getCustomName());
        ProductCatalog targetCatalog = findExistingCatalogMatch(normalizedRawName)
                .orElseGet(() -> createCatalogEntry(normalizedRawName, candidate));

        if (candidate.getStoreName() != null && candidate.getPrice() != null) {
            storePriceService.recordStorePrice(targetCatalog, candidate.getStoreName(), candidate.getPrice());
        }

        return userProductHistoryRepository.linkUnknownHistoryToCatalog(normalizedRawName, targetCatalog) > 0;
    }

    private Optional<ProductCatalog> findExistingCatalogMatch(String rawName) {
        return productCatalogRepository.searchByKeywordStrict(rawName).stream().findFirst();
    }

    private ProductCatalog createCatalogEntry(String normalizedRawName, UserProductHistoryRepository.PopularUnknownProduct candidate) {
        // Trimitem tot pachetul de informații către noul prompt al AI-ului
        CatalogStandardizationResult standardized = catalogItemStandardizationService.standardize(
                normalizedRawName,
                candidate.getBrand(),
                candidate.getCategory(),
                candidate.getPrice()
        );

        return productCatalogRepository
                .findBySpecificNameAndBrand(standardized.cleanName(), standardized.brand())
                .orElseGet(() -> {
                    ProductCatalog productCatalog = new ProductCatalog();
                    productCatalog.setGenericName(standardized.cleanName());
                    productCatalog.setSpecificName(standardized.cleanName());
                    productCatalog.setBrand(standardized.brand());
                    productCatalog.setCategory(standardized.category());

                    // Setăm purchase count-ul inițial corect (ex: 3), nu 0!
                    Integer initialCount = candidate.getUserCount() != null ? candidate.getUserCount() : 3;
                    productCatalog.setPurchaseCount(initialCount);

                    // Setăm prețul estimat din istoric dacă există
                    if (candidate.getPrice() != null) {
                        productCatalog.setEstimatedPrice(candidate.getPrice());
                    }

                    return productCatalogRepository.save(productCatalog);
                });
    }

    private String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Candidate product name must not be blank");
        }
        return value.trim();
    }
}