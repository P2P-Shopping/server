package com.p2ps.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private CatalogController catalogController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(catalogController).build();
    }

    @Test
    void getTopProductsShouldReturnOkAndList() throws Exception {
        ProductCatalog p1 = new ProductCatalog();
        p1.setId(UUID.randomUUID());
        p1.setSpecificName("Product 1");

        when(catalogService.getTopPopularProducts()).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/catalog/top")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].specificName").value("Product 1"));

        verify(catalogService).getTopPopularProducts();
    }

    @Test
    void recordPurchaseShouldReturnOkAndProductWhenValid() throws Exception {
        ProductCatalog p1 = new ProductCatalog();
        p1.setId(UUID.randomUUID());
        p1.setSpecificName("Coca Cola Zero 2L");
        p1.setPurchaseCount(1);
        p1.setCategory("Bauturi");
        p1.setEstimatedPrice(new BigDecimal("7.50"));

        when(catalogService.recordPurchase("Cola", "Coca Cola Zero 2L", "Coca Cola", "Bauturi", new BigDecimal("7.50")))
                .thenReturn(p1);

        mockMvc.perform(post("/api/catalog/record")
                        .param("genericName", "Cola")
                        .param("specificName", "Coca Cola Zero 2L")
                        .param("brand", "Coca Cola")
                        .param("category", "Bauturi")
                        .param("price", "7.50")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specificName").value("Coca Cola Zero 2L"))
                .andExpect(jsonPath("$.purchaseCount").value(1));

        verify(catalogService).recordPurchase("Cola", "Coca Cola Zero 2L", "Coca Cola", "Bauturi", new BigDecimal("7.50"));
    }

    @Test
    void recordPurchaseShouldReturnBadRequestWhenServiceReturnsNull() throws Exception {
        when(catalogService.recordPurchase(any(), any(), any(), any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/catalog/record")
                        .param("specificName", "   ") // invalid name
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(catalogService).recordPurchase(null, "   ", null, null, null);
    }

    @Test
    void getBestStoresShouldReturnOkAndListOfUUIDs() throws Exception {
        UUID catalogId = UUID.randomUUID();
        UUID storeId1 = UUID.randomUUID();
        UUID storeId2 = UUID.randomUUID();

        when(catalogService.getBestStoresForCatalogProduct(catalogId)).thenReturn(List.of(storeId1, storeId2));

        mockMvc.perform(get("/api/catalog/{catalogId}/best-stores", catalogId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(storeId1.toString()))
                .andExpect(jsonPath("$[1]").value(storeId2.toString()));

        verify(catalogService).getBestStoresForCatalogProduct(catalogId);
    }
}