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
        verify(catalogRepository, never()).save(any());
    }

    @Test
    void recordPurchaseShouldCreateNewProductWhenNotExists() {
        String specificName = "New Product";
        String brand = "New Brand";

        when(catalogRepository.findBySpecificNameAndBrand(specificName, brand))
                .thenReturn(Optional.empty());

        ProductCatalog savedProduct = new ProductCatalog();
        savedProduct.setId(UUID.randomUUID());
        savedProduct.setSpecificName(specificName);
        savedProduct.setBrand(brand);
        savedProduct.setPurchaseCount(1);
        
        when(catalogRepository.save(any(ProductCatalog.class))).thenReturn(savedProduct);

        ProductCatalog result = catalogService.recordPurchase("Generic", specificName, brand, "Category", BigDecimal.TEN);

        assertNotNull(result);
        assertEquals(specificName, result.getSpecificName());
        assertEquals(1, result.getPurchaseCount());
        
        verify(catalogRepository).save(argThat(product -> 
            product.getSpecificName().equals(specificName) && 
            product.getPurchaseCount() == 1 &&
            product.getEstimatedPrice().equals(BigDecimal.TEN)
        ));
    }

    @Test
    void recordPurchaseShouldIncrementCountAndUpdateFieldsWhenExists() {
        String specificName = "Existing Product";
        String brand = "Existing Brand";

        ProductCatalog existingProduct = new ProductCatalog();
        existingProduct.setId(UUID.randomUUID());
        existingProduct.setSpecificName(specificName);
        existingProduct.setBrand(brand);
        existingProduct.setPurchaseCount(5);
        existingProduct.setEstimatedPrice(new BigDecimal("10.00"));

        when(catalogRepository.findBySpecificNameAndBrand(specificName, brand))
                .thenReturn(Optional.of(existingProduct));
                
        when(catalogRepository.save(any(ProductCatalog.class))).thenAnswer(i -> i.getArgument(0));

        BigDecimal newPrice = new BigDecimal("12.50");
        ProductCatalog result = catalogService.recordPurchase("Generic", specificName, brand, "New Category", newPrice);

        assertNotNull(result);
        assertEquals(6, result.getPurchaseCount(), "Purchase count should be incremented");
        assertEquals(newPrice, result.getEstimatedPrice(), "Price should be updated");
        assertEquals("New Category", result.getCategory(), "Category should be updated");
        
        verify(catalogRepository).save(existingProduct);
    }
    
    @Test
    void recordPurchaseShouldNotUpdateFieldsWhenNullProvidedForExistingProduct() {
        String specificName = "Existing Product";
        String brand = "Existing Brand";

        ProductCatalog existingProduct = new ProductCatalog();
        existingProduct.setId(UUID.randomUUID());
        existingProduct.setSpecificName(specificName);
        existingProduct.setBrand(brand);
        existingProduct.setPurchaseCount(5);
        existingProduct.setCategory("Old Category");
        existingProduct.setEstimatedPrice(new BigDecimal("10.00"));

        when(catalogRepository.findBySpecificNameAndBrand(specificName, brand))
                .thenReturn(Optional.of(existingProduct));
                
        when(catalogRepository.save(any(ProductCatalog.class))).thenAnswer(i -> i.getArgument(0));

        ProductCatalog result = catalogService.recordPurchase("Generic", specificName, brand, null, null);

        assertNotNull(result);
        assertEquals(6, result.getPurchaseCount(), "Purchase count should be incremented");
        assertEquals(new BigDecimal("10.00"), result.getEstimatedPrice(), "Price should not change");
        assertEquals("Old Category", result.getCategory(), "Category should not change");
        
        verify(catalogRepository).save(existingProduct);
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