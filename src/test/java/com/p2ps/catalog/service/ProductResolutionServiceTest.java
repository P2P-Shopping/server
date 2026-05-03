package com.p2ps.catalog.service;

import com.p2ps.auth.model.User;
import com.p2ps.auth.repo.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductResolutionServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductResolutionService productResolutionService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("test@user.com", "pass");
        user.setId(1);
    }

    @Test
    void resolveForUser_userHistoryMatch_returnsResolvedProductFromHistory() {
        when(userRepository.findByEmail("test@user.com")).thenReturn(Optional.of(user));

        ItemRepository.UserProductHistoryMatch historyMatch = mock(ItemRepository.UserProductHistoryMatch.class);
        when(historyMatch.getCatalogGenericName()).thenReturn("oua");
        when(historyMatch.getCatalogSpecificName()).thenReturn("Oua de gaina M");
        when(historyMatch.getBrand()).thenReturn("Ferma");
        when(historyMatch.getCategory()).thenReturn("Lactate");
        when(historyMatch.getCatalogId()).thenReturn(UUID.randomUUID());

        when(itemRepository.findUserProductHistoryMatches(user.getId(), "ou")).thenReturn(List.of(historyMatch));

        Optional<ProductResolutionService.ResolvedProduct> result = productResolutionService.resolveForUser("ou", "test@user.com");

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo("USER_HISTORY");
        assertThat(result.get().genericName()).isEqualTo("oua");
        assertThat(result.get().specificName()).isEqualTo("Oua de gaina M");

        verify(productCatalogRepository, never()).findTopByFuzzySearch(anyString());
    }

    @Test
    void resolveForUser_globalCatalogMatch_returnsResolvedProductFromCatalog() {
        when(userRepository.findByEmail("test@user.com")).thenReturn(Optional.of(user));
        when(itemRepository.findUserProductHistoryMatches(user.getId(), "ou")).thenReturn(Collections.emptyList());

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setGenericName("oua");
        catalogProduct.setSpecificName("Oua de gaina");
        catalogProduct.setBrand("BrandX");

        when(productCatalogRepository.findTopByFuzzySearch("ou")).thenReturn(Optional.of(catalogProduct));

        Optional<ProductResolutionService.ResolvedProduct> result = productResolutionService.resolveForUser("ou", "test@user.com");

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo("GLOBAL_CATALOG");
        assertThat(result.get().genericName()).isEqualTo("oua");
        assertThat(result.get().specificName()).isEqualTo("Oua de gaina");
    }

    @Test
    void resolveForUser_noMatch_returnsEmpty() {
        when(userRepository.findByEmail("test@user.com")).thenReturn(Optional.of(user));
        when(itemRepository.findUserProductHistoryMatches(user.getId(), "unknown")).thenReturn(Collections.emptyList());
        when(productCatalogRepository.findTopByFuzzySearch("unknown")).thenReturn(Optional.empty());

        Optional<ProductResolutionService.ResolvedProduct> result = productResolutionService.resolveForUser("unknown", "test@user.com");

        assertThat(result).isNotPresent();
    }

    @Test
    void resolveForUser_noUser_onlyChecksGlobalCatalog() {
        when(productCatalogRepository.findTopByFuzzySearch("ou")).thenReturn(Optional.empty());

        Optional<ProductResolutionService.ResolvedProduct> result = productResolutionService.resolveForUser("ou", null);

        assertThat(result).isNotPresent();
        verify(userRepository, never()).findByEmail(anyString());
        verify(itemRepository, never()).findUserProductHistoryMatches(anyInt(), anyString());
    }
}
