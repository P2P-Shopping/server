package com.p2ps.catalog.service;

import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.ItemRepository;
import com.p2ps.auth.model.Users;
import com.p2ps.util.ProductStringUtils;
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
    private final ItemRepository itemRepository;
    private final ProductCatalogRepository productCatalogRepository;

    public ProductResolutionService(
            UserRepository userRepository,
            ItemRepository itemRepository,
            ProductCatalogRepository productCatalogRepository
    ) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.productCatalogRepository = productCatalogRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedProduct> resolveForUser(String rawKeyword, String userEmail) {
        String keyword = normalize(rawKeyword);
        if (keyword == null) {
            return Optional.empty();
        }

        if (userEmail != null && !userEmail.isBlank()) {
            Optional<Users> user = userRepository.findByEmail(userEmail);
            if (user.isPresent()) {
                return resolveForUserInternal(keyword, user.get());
            }
        }

        return resolveInCatalogOnly(keyword);
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedProduct> resolveForUser(String rawKeyword, Users user) {
        String keyword = normalize(rawKeyword);
        if (keyword == null) {
            return Optional.empty();
        }

        return resolveForUserInternal(keyword, user);
    }

    private Optional<ResolvedProduct> resolveForUserInternal(String keyword, Users user) {
        if (user != null) {
            Optional<ResolvedProduct> historyMatch = resolveFromUserHistory(keyword, user);
            if (historyMatch.isPresent()) {
                return historyMatch;
            }
        }

        return resolveInCatalogOnly(keyword);
    }

    private Optional<ResolvedProduct> resolveInCatalogOnly(String keyword) {
        return productCatalogRepository.searchByKeywordFuzzy(keyword).stream()
                .findFirst()
                .map(product -> new ResolvedProduct(
                        ProductStringUtils.firstNonBlank(product.getGenericName(), product.getSpecificName()),
                        product.getBrand(),
                        product.getCategory(),
                        product,
                        "GLOBAL_CATALOG"
                ));
    }

    private Optional<ResolvedProduct> resolveFromUserHistory(String keyword, Users user) {
        if (user == null) {
            return Optional.empty();
        }

        return itemRepository.findUserProductHistoryMatches(user.getId(), keyword).stream()
                .findFirst()
                .map(match -> {
                    ProductCatalog catalogProduct = null;
                    if (match.getCatalogId() != null) {
                        catalogProduct = new ProductCatalog();
                        catalogProduct.setId(match.getCatalogId());
                        catalogProduct.setGenericName(match.getCatalogGenericName());
                        catalogProduct.setSpecificName(match.getCatalogSpecificName());
                        catalogProduct.setBrand(match.getBrand());
                        catalogProduct.setCategory(match.getCategory());
                    }

                    return new ResolvedProduct(
                            ProductStringUtils.firstNonBlank(
                                    match.getItemName(),
                                    match.getCatalogGenericName(),
                                    match.getCatalogSpecificName()
                            ),
                            match.getBrand(),
                            match.getCategory(),
                            catalogProduct,
                            "USER_HISTORY"
                    );
                });
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

}
