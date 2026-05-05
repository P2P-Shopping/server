package com.p2ps.catalog.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.dto.ProductSuggestionDTO;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private ProductCatalogRepository catalogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProductHistoryRepository userProductHistoryRepository;

    @InjectMocks
    private CatalogService catalogService;

    // --- TESTE EXISTENTE REVIZUITE ---

    @Test
    void recordPurchaseShouldReturnNullWhenSpecificNameIsBlank() {
        ProductCatalog result = catalogService.recordPurchase("Generic", " ", "Brand", "Category", BigDecimal.TEN);
        assertNull(result);
        verify(catalogRepository, never()).upsertProduct(any(), any(), any(), any(), any());
    }

    @Test
    void recordPurchaseShouldCallUpsertAndReturnProduct() {
        String specificName = "New Product";
        String brand = "New Brand";
        ProductCatalog mockProduct = new ProductCatalog();
        mockProduct.setSpecificName(specificName);
        mockProduct.setBrand(brand);

        when(catalogRepository.findBySpecificNameAndBrand(specificName, brand)).thenReturn(Optional.of(mockProduct));

        ProductCatalog result = catalogService.recordPurchase("Generic", specificName, brand, "Category", BigDecimal.TEN);

        verify(catalogRepository).upsertProduct("Generic", specificName, brand, "Category", BigDecimal.TEN);
        assertNotNull(result);
    }

    @Test
    void searchProductsByNameShouldReturnMatchesWhenKeywordIsValid() {
        ProductCatalog p1 = new ProductCatalog();
        p1.setSpecificName("Lapte");
        when(catalogRepository.searchByKeywordFuzzy("lapte")).thenReturn(List.of(p1));

        List<ProductCatalog> result = catalogService.searchProductsByName("  lapte  ");

        assertEquals(1, result.size());
        verify(catalogRepository).searchByKeywordFuzzy("lapte");
    }

    // --- TESTE NOI PENTRU TASK 4 (suggestProducts) ---

    @Test
    void suggestProductsShouldReturnEmptyListWhenKeywordIsBlank() {
        assertTrue(catalogService.suggestProducts(null, "user@test.com").isEmpty());
        assertTrue(catalogService.suggestProducts("  ", "user@test.com").isEmpty());
    }

    @Test
    void suggestProductsShouldPrioritizeUserHistory() {
        // Setup user
        String email = "user@test.com";
        Integer userId = 1; // MODIFICAT: din UUID în Integer
        Users user = new Users();
        user.setId(userId);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserProductHistoryRepository.HistoryMatch match = mock(UserProductHistoryRepository.HistoryMatch.class);
        when(match.getItemName()).thenReturn("Produs din Istoric");
        when(match.getBrand()).thenReturn("BrandIstoric");
        when(match.getPrice()).thenReturn(BigDecimal.valueOf(10));

        when(userProductHistoryRepository.findMatches(userId, "apa")).thenReturn(List.of(match));
        when(catalogRepository.searchByKeywordFuzzy("apa")).thenReturn(List.of());

        // Act
        List<ProductSuggestionDTO> results = catalogService.suggestProducts("apa", email);

        // Assert
        assertFalse(results.isEmpty());
        // MODIFICAT: Dacă folosești record sau getter diferit, ajustează aici (ex: getItemName())
        assertEquals("Produs din Istoric", results.get(0).name());
        verify(userProductHistoryRepository).findMatches(userId, "apa");
    }

    @Test
    void suggestProductsShouldFallbackToGlobalCatalogAndAvoidDuplicates() {
        String email = "user@test.com";
        Integer userId = 2; // MODIFICAT: din UUID în Integer
        Users user = new Users();
        user.setId(userId);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserProductHistoryRepository.HistoryMatch historyMatch = mock(UserProductHistoryRepository.HistoryMatch.class);
        when(historyMatch.getItemName()).thenReturn("Apa Plata");

        ProductCatalog catalogMatch = new ProductCatalog();
        catalogMatch.setSpecificName("Apa Plata");
        catalogMatch.setBrand("Dorna");

        when(userProductHistoryRepository.findMatches(userId, "apa")).thenReturn(List.of(historyMatch));
        when(catalogRepository.searchByKeywordFuzzy("apa")).thenReturn(List.of(catalogMatch));

        // Act
        List<ProductSuggestionDTO> results = catalogService.suggestProducts("apa", email);

        // Assert
        assertEquals(1, results.size());
        assertEquals("Apa Plata", results.get(0).name()); // Ajustează dacă e necesar
    }

    @Test
    void suggestProductsShouldLimitResultsTo10() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        List<ProductCatalog> largeCatalog = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ProductCatalog p = new ProductCatalog();
            p.setSpecificName("Produs " + i);
            largeCatalog.add(p);
        }
        when(catalogRepository.searchByKeywordFuzzy("test")).thenReturn(largeCatalog);

        // Act
        List<ProductSuggestionDTO> results = catalogService.suggestProducts("test", "user@test.com");

        // Assert
        assertEquals(10, results.size());
    }

    @Test
    void suggestProductsShouldWorkEvenIfUserNotFound() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        ProductCatalog p = new ProductCatalog();
        p.setSpecificName("Produs Global");
        when(catalogRepository.searchByKeywordFuzzy("test")).thenReturn(List.of(p));

        // Act
        List<ProductSuggestionDTO> results = catalogService.suggestProducts("test", "unknown@test.com");

        // Assert
        assertEquals(1, results.size());
        assertEquals("Produs Global", results.get(0).name()); // Ajustează dacă e necesar
        verify(userProductHistoryRepository, never()).findMatches(any(), any());
    }
}