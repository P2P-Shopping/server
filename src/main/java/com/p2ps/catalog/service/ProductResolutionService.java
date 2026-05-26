package com.p2ps.catalog.service;

import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProductResolutionService {

    public record ResolvedProduct(
            String matchedName,
            String brand,
            String category,
            ProductCatalog catalogProduct,
            String source
    ) {}

    private final UserRepository userRepository;
    private final UserProductHistoryRepository historyRepository;
    private final ProductCatalogRepository productCatalogRepository;

    public ProductResolutionService(
            UserRepository userRepository,
            UserProductHistoryRepository historyRepository,
            ProductCatalogRepository productCatalogRepository
    ) {
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.productCatalogRepository = productCatalogRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedProduct> resolveForUser(String rawKeyword, String userEmail) {
        String keyword = normalize(rawKeyword);
        if (keyword == null) {
            return Optional.empty();
        }

        Optional<ResolvedProduct> historyMatch = resolveFromUserHistory(keyword, userEmail);
        if (historyMatch.isPresent()) {
            return historyMatch;
        }

        return productCatalogRepository.searchByKeywordFuzzy(keyword).stream()
                .findFirst()
                .map(product -> new ResolvedProduct(
                        firstNonBlank(product.getGenericName(), product.getSpecificName()),
                        product.getBrand(),
                        product.getCategory(),
                        product,
                        "GLOBAL_CATALOG"
                ));
    }

    private Optional<ResolvedProduct> resolveFromUserHistory(String keyword, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return Optional.empty();
        }

        return userRepository.findByEmail(userEmail)
                // AICI ERA GRESEALA: am schimbat itemRepository cu historyRepository.findMatches
                .flatMap(user -> historyRepository.findMatches(user.getId(), keyword).stream()
                        .findFirst()
                        .map(match -> {
                            ProductCatalog catalogProduct = null;
                            if (match.getCatalogId() != null) {
                                catalogProduct = new ProductCatalog();
                                catalogProduct.setId(match.getCatalogId());
                                catalogProduct.setGenericName(match.getCatalogGenericName());
                                catalogProduct.setSpecificName(match.getCatalogSpecificName());
                                catalogProduct.setDefaultQuantity(match.getCatalogDefaultQuantity());
                                catalogProduct.setBrand(match.getBrand());
                                catalogProduct.setCategory(match.getCategory());
                            }

                            return new ResolvedProduct(
                                    firstNonBlank(
                                            match.getItemName(),
                                            match.getCatalogGenericName(),
                                            match.getCatalogSpecificName()
                                    ),
                                    match.getBrand(),
                                    match.getCategory(),
                                    catalogProduct,
                                    "USER_HISTORY"
                            );
                        }));
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
