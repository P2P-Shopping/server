package com.p2ps.catalog.service;

import com.p2ps.ai.service.CatalogItemStandardizationService;
import com.p2ps.ai.service.CatalogStandardizationResult;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalCatalogPopulationServiceTest {

    @Mock
    private UserProductHistoryRepository userProductHistoryRepository;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private CatalogItemStandardizationService catalogItemStandardizationService;

    private GlobalCatalogPopulationService service;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(new ImmediateTransactionManager());
        service = new GlobalCatalogPopulationService(
                userProductHistoryRepository,
                productCatalogRepository,
                catalogItemStandardizationService,
                transactionTemplate
        );
    }

    @Test
    void populateFromPopularUnknownProductsShouldReturnZeroWhenNoCandidatesExist() {
        when(userProductHistoryRepository.findPopularUnknownProducts(3)).thenReturn(List.of());

        int result = service.populateFromPopularUnknownProducts(3);

        assertThat(result).isZero();
        verify(productCatalogRepository, never()).searchByKeywordStrict(any());
    }

    @Test
    void populateFromPopularUnknownProductsShouldLinkExistingCatalogMatches() {
        UUID catalogId = UUID.randomUUID();
        ProductCatalog existingCatalog = new ProductCatalog();
        existingCatalog.setId(catalogId);
        existingCatalog.setSpecificName("Whole Milk");

        UserProductHistoryRepository.PopularUnknownProduct candidate = candidate("milk", 4);
        when(userProductHistoryRepository.findPopularUnknownProducts(3)).thenReturn(List.of(candidate));
        when(productCatalogRepository.searchByKeywordStrict("milk")).thenReturn(List.of(existingCatalog));
        when(userProductHistoryRepository.linkUnknownHistoryToCatalog("milk", existingCatalog)).thenReturn(3);

        int result = service.populateFromPopularUnknownProducts(3);

        assertThat(result).isEqualTo(1);
        verify(catalogItemStandardizationService, never()).standardize(any(), any(), any(), any());
        verify(productCatalogRepository, never()).save(any());
    }

    @Test
    void populateFromPopularUnknownProductsShouldCreateCatalogEntryWhenMissing() {
        ProductCatalog savedCatalog = new ProductCatalog();
        savedCatalog.setId(UUID.randomUUID());
        UserProductHistoryRepository.PopularUnknownProduct candidate = candidate("sana", 5);

        when(userProductHistoryRepository.findPopularUnknownProducts(3)).thenReturn(List.of(candidate));
        when(productCatalogRepository.searchByKeywordStrict("sana")).thenReturn(List.of());
        when(catalogItemStandardizationService.standardize("sana", null, null, null))
                .thenReturn(new CatalogStandardizationResult("Sana 3.5%", "Dairy", "Napolact", "500 ml"));
        when(productCatalogRepository.findBySpecificNameAndBrand("Sana 3.5%", "Napolact")).thenReturn(Optional.empty());
        when(productCatalogRepository.save(any(ProductCatalog.class))).thenReturn(savedCatalog);
        when(userProductHistoryRepository.linkUnknownHistoryToCatalog("sana", savedCatalog)).thenReturn(6);

        int result = service.populateFromPopularUnknownProducts(3);

        assertThat(result).isEqualTo(1);
        verify(catalogItemStandardizationService).standardize("sana", null, null, null);
        verify(productCatalogRepository).save(any(ProductCatalog.class));
    }

    @Test
    void populateFromPopularUnknownProductsShouldReuseCatalogCreatedEarlier() {
        ProductCatalog existingCatalog = new ProductCatalog();
        existingCatalog.setId(UUID.randomUUID());
        existingCatalog.setSpecificName("Greek Yogurt");
        UserProductHistoryRepository.PopularUnknownProduct candidate = candidate("iaurt grecesc", 3);

        when(userProductHistoryRepository.findPopularUnknownProducts(3)).thenReturn(List.of(candidate));
        when(productCatalogRepository.searchByKeywordStrict("iaurt grecesc")).thenReturn(List.of());
        when(catalogItemStandardizationService.standardize("iaurt grecesc", null, null, null))
                .thenReturn(new CatalogStandardizationResult("Greek Yogurt", "Dairy", "Olympus", "1 buc"));
        when(productCatalogRepository.findBySpecificNameAndBrand("Greek Yogurt", "Olympus")).thenReturn(Optional.of(existingCatalog));
        when(userProductHistoryRepository.linkUnknownHistoryToCatalog("iaurt grecesc", existingCatalog)).thenReturn(2);

        int result = service.populateFromPopularUnknownProducts(3);

        assertThat(result).isEqualTo(1);
        verify(productCatalogRepository, never()).save(any());
    }

    @Test
    void populateFromPopularUnknownProductsShouldContinueWhenOneCandidateFails() {
        UserProductHistoryRepository.PopularUnknownProduct broken = candidate("bad item", 3);
        UserProductHistoryRepository.PopularUnknownProduct good = candidate("good item", 4);
        ProductCatalog existingCatalog = new ProductCatalog();
        existingCatalog.setId(UUID.randomUUID());

        when(userProductHistoryRepository.findPopularUnknownProducts(3)).thenReturn(List.of(broken, good));
        when(productCatalogRepository.searchByKeywordStrict("bad item")).thenThrow(new IllegalStateException("boom"));
        when(productCatalogRepository.searchByKeywordStrict("good item")).thenReturn(List.of(existingCatalog));
        when(userProductHistoryRepository.linkUnknownHistoryToCatalog("good item", existingCatalog)).thenReturn(1);

        int result = service.populateFromPopularUnknownProducts(3);

        assertThat(result).isEqualTo(1);
        verify(userProductHistoryRepository).linkUnknownHistoryToCatalog("good item", existingCatalog);
    }

    @Test
    void populateFromPopularUnknownProductsShouldSkipBlankCandidateNamesAndContinue() {
        UserProductHistoryRepository.PopularUnknownProduct broken = candidate("   ", 3);
        UserProductHistoryRepository.PopularUnknownProduct good = candidate("lapte", 4);
        ProductCatalog existingCatalog = new ProductCatalog();
        existingCatalog.setId(UUID.randomUUID());

        when(userProductHistoryRepository.findPopularUnknownProducts(3)).thenReturn(List.of(broken, good));
        when(productCatalogRepository.searchByKeywordStrict("lapte")).thenReturn(List.of(existingCatalog));
        when(userProductHistoryRepository.linkUnknownHistoryToCatalog("lapte", existingCatalog)).thenReturn(1);

        int result = service.populateFromPopularUnknownProducts(3);

        assertThat(result).isEqualTo(1);
        verify(userProductHistoryRepository).linkUnknownHistoryToCatalog("lapte", existingCatalog);
    }

    private UserProductHistoryRepository.PopularUnknownProduct candidate(String customName, long distinctUsers) {
        return candidate(customName, distinctUsers, null, null, null);
    }

    private UserProductHistoryRepository.PopularUnknownProduct candidate(
            String customName,
            long distinctUsers,
            String brand,
            String category,
            BigDecimal price) {
        return new UserProductHistoryRepository.PopularUnknownProduct() {
            @Override
            public String getCustomName() {
                return customName;
            }

            @Override
            public Integer getUserCount() {
                return (int) distinctUsers;
            }

            @Override
            public String getBrand() {
                return brand;
            }

            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public BigDecimal getPrice() {
                return price;
            }
        };
    }

    private static class ImmediateTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // No-op: transactional side effects are irrelevant for these isolated unit tests.
        }

        @Override
        public void rollback(TransactionStatus status) {
            // No-op: rollback behavior is not exercised by this in-memory transaction stub.
        }
    }
}

