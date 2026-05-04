package com.p2ps.catalog.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
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
class ProductResolutionServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductResolutionService productResolutionService;

    private Users user;

    @BeforeEach
    void setUp() {
        user = new Users("test@user.com", "pass", "Test", "User");
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
        assertThat(result.get().matchedName()).isEqualTo("oua");
        assertThat(result.get().catalogProduct().getSpecificName()).isEqualTo("Oua de gaina M");

        verify(productCatalogRepository, never()).searchByKeywordFuzzy(anyString());
    }

    @Test
    void resolveForUser_globalCatalogMatch_returnsResolvedProductFromCatalog() {
        when(userRepository.findByEmail("test@user.com")).thenReturn(Optional.of(user));
        when(itemRepository.findUserProductHistoryMatches(user.getId(), "ou")).thenReturn(Collections.emptyList());

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setGenericName("oua");
        catalogProduct.setSpecificName("Oua de gaina");
        catalogProduct.setBrand("BrandX");

        when(productCatalogRepository.searchByKeywordFuzzy("ou")).thenReturn(List.of(catalogProduct));

        Optional<ProductResolutionService.ResolvedProduct> result = productResolutionService.resolveForUser("ou", "test@user.com");

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo("GLOBAL_CATALOG");
        assertThat(result.get().matchedName()).isEqualTo("oua");
        assertThat(result.get().catalogProduct().getSpecificName()).isEqualTo("Oua de gaina");
    }

    @Test
    void resolveForUser_noMatch_returnsEmpty() {
        when(userRepository.findByEmail("test@user.com")).thenReturn(Optional.of(user));
        when(itemRepository.findUserProductHistoryMatches(user.getId(), "unknown")).thenReturn(Collections.emptyList());
        when(productCatalogRepository.searchByKeywordFuzzy("unknown")).thenReturn(Collections.emptyList());

        Optional<ProductResolutionService.ResolvedProduct> result = productResolutionService.resolveForUser("unknown", "test@user.com");

        assertThat(result).isNotPresent();
    }

    @Test
    void resolveForUser_noUser_onlyChecksGlobalCatalog() {
        when(productCatalogRepository.searchByKeywordFuzzy("ou")).thenReturn(Collections.emptyList());

        Optional<ProductResolutionService.ResolvedProduct> result = productResolutionService.resolveForUser("ou", null);

        assertThat(result).isNotPresent();
        verify(userRepository, never()).findByEmail(anyString());
        verify(itemRepository, never()).findUserProductHistoryMatches(anyInt(), anyString());
    }
}
