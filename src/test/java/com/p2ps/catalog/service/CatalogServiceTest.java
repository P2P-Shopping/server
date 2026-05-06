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
import java.util.UUID;

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

    // --- TESTE EXISTENTE COMBINATE ȘI REVIZUITE ---

    @Test
    void recordPurchaseShouldReturnNullWhenSpecificNameIsBlank() {
        ProductCatalog result = catalogService.recordPurchase("Generic", " ", "Brand", "Category", BigDecimal.TEN);
        assertNull(result, "Should return null when specific name is blank");
        verify(catalogRepository, never()).upsertProduct(any(), any(), any(), any(), any());
    }

    @Test
    void recordPurchaseShouldCallUpsertAndReturnProduct() {
        String specificName = "New Product";
        String brand = "New Brand";
        String category = "Category";
        BigDecimal price = BigDecimal.TEN;
        String genericName = "Generic";

        ProductCatalog mockProduct = new ProductCatalog();
        mockProduct.setSpecificName(specificName);
        mockProduct.setBrand(brand);

        when(catalogRepository.findBySpecificNameAndBrand(specificName, brand)).thenReturn(Optional.of(mockProduct));

        ProductCatalog result = catalogService.recordPurchase(genericName, specificName, brand, category, price);

        verify(catalogRepository).upsertProduct(genericName, specificName, brand, category, price);
        assertNotNull(result);
        assertEquals(specificName, result.getSpecificName());
    }

    @Test
    void recordPurchaseShouldHandleNullGenericName() {
        String specificName = "Product without generic name";
        String brand = "Brand";

        when(catalogRepository.findBySpecificNameAndBrand(specificName, brand)).thenReturn(Optional.of(new ProductCatalog()));

        catalogService.recordPurchase(null, specificName, brand, "Category", BigDecimal.ONE);

        verify(catalogRepository).upsertProduct(eq("Unknown"), eq(specificName), eq(brand), any(), any());
    }

    @Test
    void recordPurchaseShouldThrowExceptionWhenProductNotFoundAfterUpsert() {
        String specificName = "Ghost Product";
        String brand = "Ghost Brand";

        when(catalogRepository.findBySpecificNameAndBrand(specificName, brand)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            catalogService.recordPurchase("Generic", specificName, brand, "Category", BigDecimal.TEN);
        });

        assertTrue(exception.getMessage().contains("Product should have been created by upsert"));
    }

    @Test
    void getTopPopularProductsShouldReturnListFromRepository() {
        ProductCatalog p1 = new ProductCatalog();
        p1.setSpecificName("P1");
        ProductCatalog p2 = new ProductCatalog();
        p2.setSpecificName("P2");

        List<ProductCatalog> expectedList = List.of(p1, p2);

        when(catalogRepository.findTop50ByOrderByPurchaseCountDesc()).thenReturn(expectedList);

        List<ProductCatalog> result = catalogService.getTopPopularProducts();

        assertEquals(2, result.size());
        assertEquals(expectedList, result);
        verify(catalogRepository).findTop50ByOrderByPurchaseCountDesc();
    }

    @Test
    void getBestStoresForCatalogProductShouldReturnListFromRepository() {
        UUID catalogId = UUID.randomUUID();
        UUID store1 = UUID.randomUUID();
        UUID store2 = UUID.randomUUID();

        List<UUID> expectedStores = List.of(store1, store2);

        when(catalogRepository.findBestStoresForCatalogProduct(catalogId)).thenReturn(expectedStores);

        List<UUID> result = catalogService.getBestStoresForCatalogProduct(catalogId);

        assertEquals(2, result.size());
        assertEquals(expectedStores, result);
        verify(catalogRepository).findBestStoresForCatalogProduct(catalogId);
    }

    @Test
    void searchProductsByNameShouldReturnEmptyListWhenKeywordIsNull() {
        List<ProductCatalog> result = catalogService.searchProductsByName(null);

        assertTrue(result.isEmpty());
        verify(catalogRepository, never()).searchByKeyword(any());
    }

    @Test
    void searchProductsByNameShouldReturnEmptyListWhenKeywordIsBlank() {
        List<ProductCatalog> result = catalogService.searchProductsByName("   ");

        assertTrue(result.isEmpty());
        verify(catalogRepository, never()).searchByKeyword(any());
    }

    @Test
    void searchProductsByNameShouldReturnMatchesWhenKeywordIsValid() {
        ProductCatalog p1 = new ProductCatalog();
        p1.setSpecificName("Lapte");

        when(catalogRepository.searchByKeyword("lapte")).thenReturn(List.of(p1));

        List<ProductCatalog> result = catalogService.searchProductsByName("  lapte  ");

        assertEquals(1, result.size());
        verify(catalogRepository).searchByKeyword("lapte");
    }

    // --- TESTE NOI PENTRU TASK 4 (suggestProducts) ---

    @Test
    void suggestProductsShouldReturnEmptyListWhenKeywordIsBlank() {
        assertTrue(catalogService.suggestProducts(null, "user@test.com").isEmpty());
        assertTrue(catalogService.suggestProducts("  ", "user@test.com").isEmpty());
    }

    @Test
    void suggestProductsShouldPrioritizeUserHistory() {
        String email = "user@test.com";
        Integer userId = 1;
        Users user = new Users();
        user.setId(userId);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserProductHistoryRepository.HistoryMatch match = mock(UserProductHistoryRepository.HistoryMatch.class);
        when(match.getItemName()).thenReturn("Produs din Istoric");
        when(match.getBrand()).thenReturn("BrandIstoric");
        when(match.getPrice()).thenReturn(BigDecimal.valueOf(10));

        when(userProductHistoryRepository.findMatches(userId, "apa")).thenReturn(List.of(match));
        when(catalogRepository.searchByKeyword("apa")).thenReturn(List.of());

        List<ProductSuggestionDTO> results = catalogService.suggestProducts("apa", email);

        assertFalse(results.isEmpty());
        assertEquals("Produs din Istoric", results.get(0).name());
        verify(userProductHistoryRepository).findMatches(userId, "apa");
    }

    @Test
    void suggestProductsShouldFallbackToGlobalCatalogAndAvoidDuplicates() {
        String email = "user@test.com";
        Integer userId = 2;
        Users user = new Users();
        user.setId(userId);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserProductHistoryRepository.HistoryMatch historyMatch = mock(UserProductHistoryRepository.HistoryMatch.class);
        when(historyMatch.getItemName()).thenReturn("Apa Plata");

        ProductCatalog catalogMatch = new ProductCatalog();
        catalogMatch.setSpecificName("Apa Plata");
        catalogMatch.setBrand("Dorna");

        when(userProductHistoryRepository.findMatches(userId, "apa")).thenReturn(List.of(historyMatch));
        when(catalogRepository.searchByKeyword("apa")).thenReturn(List.of(catalogMatch));

        List<ProductSuggestionDTO> results = catalogService.suggestProducts("apa", email);

        assertEquals(1, results.size());
        assertEquals("Apa Plata", results.get(0).name());
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
        when(catalogRepository.searchByKeyword("test")).thenReturn(largeCatalog);

        List<ProductSuggestionDTO> results = catalogService.suggestProducts("test", "user@test.com");

        assertEquals(10, results.size());
    }

    @Test
    void suggestProductsShouldWorkEvenIfUserNotFound() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        ProductCatalog p = new ProductCatalog();
        p.setSpecificName("Produs Global");
        when(catalogRepository.searchByKeyword("test")).thenReturn(List.of(p));

        List<ProductSuggestionDTO> results = catalogService.suggestProducts("test", "unknown@test.com");

        assertEquals(1, results.size());
        assertEquals("Produs Global", results.get(0).name());
        verify(userProductHistoryRepository, never()).findMatches(any(), any());
    }
}