package com.p2ps.catalog.service;

import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private ProductCatalogRepository catalogRepository;

    @InjectMocks
    private CatalogService catalogService;

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

        // Verify upsert was called correctly
        verify(catalogRepository).upsertProduct(genericName, specificName, brand, category, price);
        
        // Verify the service returns the product found after upsert
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
}