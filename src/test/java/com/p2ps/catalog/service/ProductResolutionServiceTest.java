package com.p2ps.catalog.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductResolutionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProductHistoryRepository historyRepository;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @InjectMocks
    private ProductResolutionService service;

    private Users testUser;
    private String userEmail = "test@user.com";

    @BeforeEach
    void setUp() {
        testUser = new Users();
        testUser.setId(1);
        testUser.setEmail(userEmail);
    }

    @Test
    void resolveForUser_whenKeywordIsNull_returnsEmpty() {
        assertThat(service.resolveForUser(null, userEmail)).isEmpty();
    }

    @Test
    void resolveForUser_whenKeywordIsEmpty_returnsEmpty() {
        assertThat(service.resolveForUser("  ", userEmail)).isEmpty();
    }

    @Test
    void resolveForUser_withHistoryMatch_returnsHistoryResult() {
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(testUser));
        
        UserProductHistoryRepository.HistoryMatch history = mock(UserProductHistoryRepository.HistoryMatch.class);
        when(history.getItemName()).thenReturn("Lapte");
        when(history.getBrand()).thenReturn("Zuzu");
        when(history.getCategory()).thenReturn("Lactate");
        
        when(historyRepository.findMatches(1, "lapte")).thenReturn(List.of(history));

        var result = service.resolveForUser("lapte", userEmail);

        assertThat(result).isPresent();
        assertThat(result.get().matchedName()).isEqualTo("Lapte");
        assertThat(result.get().brand()).isEqualTo("Zuzu");
        assertThat(result.get().source()).isEqualTo("USER_HISTORY");
    }

    @Test
    void resolveForUser_withHistoryMatchAndCatalogId_returnsHistoryResultWithCatalog() {
        UUID catalogId = UUID.randomUUID();
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(testUser));

        UserProductHistoryRepository.HistoryMatch history = mock(UserProductHistoryRepository.HistoryMatch.class);
        when(history.getCatalogId()).thenReturn(catalogId);
        when(history.getCatalogGenericName()).thenReturn("Lapte");
        when(history.getBrand()).thenReturn("Zuzu");

        when(historyRepository.findMatches(1, "lapte")).thenReturn(List.of(history));

        var result = service.resolveForUser("lapte", userEmail);

        assertThat(result).isPresent();
        assertThat(result.get().catalogProduct()).isNotNull();
        assertThat(result.get().catalogProduct().getId()).isEqualTo(catalogId);
    }

    @Test
    void resolveForUser_withNoHistoryButGlobalCatalogMatch_returnsCatalogResult() {
        // No history
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(testUser));
        when(historyRepository.findMatches(1, "paine")).thenReturn(List.of());

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setGenericName("Paine");
        catalogProduct.setBrand("Vel Pitar");

        when(productCatalogRepository.searchByKeywordFuzzy("paine")).thenReturn(List.of(catalogProduct));

        var result = service.resolveForUser("paine", userEmail);

        assertThat(result).isPresent();
        assertThat(result.get().matchedName()).isEqualTo("Paine");
        assertThat(result.get().brand()).isEqualTo("Vel Pitar");
        assertThat(result.get().source()).isEqualTo("GLOBAL_CATALOG");
    }

    @Test
    void resolveForUser_whenUserNotFound_fallsBackToCatalog() {
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.empty());

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setGenericName("Apa");

        when(productCatalogRepository.searchByKeywordFuzzy("apa")).thenReturn(List.of(catalogProduct));

        var result = service.resolveForUser("apa", userEmail);

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo("GLOBAL_CATALOG");
    }

    @Test
    void resolveForUser_whenUserEmailIsNull_fallsBackToCatalog() {
        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setGenericName("Apa");

        when(productCatalogRepository.searchByKeywordFuzzy("apa")).thenReturn(List.of(catalogProduct));

        var result = service.resolveForUser("apa", null);

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo("GLOBAL_CATALOG");
    }

    @Test
    void resolveForUser_noMatchFound_returnsEmpty() {
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(testUser));
        when(historyRepository.findMatches(1, "unknown")).thenReturn(List.of());
        when(productCatalogRepository.searchByKeywordFuzzy("unknown")).thenReturn(List.of());

        assertThat(service.resolveForUser("unknown", userEmail)).isEmpty();
    }
}
