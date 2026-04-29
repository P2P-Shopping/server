package com.p2ps.controller;

import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.service.AiOrchestrationService;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.service.ShoppingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingControllerTest {

    @Mock
    private ShoppingListService shoppingListService;

    @Mock
    private AiOrchestrationService aiOrchestrationService;

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private ShoppingController shoppingController;

    private UUID listId;
    private String userEmail;
    private ShoppingListDTO shoppingListDTO;

    @BeforeEach
    void setUp() {
        listId = UUID.randomUUID();
        userEmail = "test@example.com";
        shoppingListDTO = new ShoppingListDTO();
        shoppingListDTO.setId(listId);

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        org.mockito.Mockito.lenient().when(authentication.getName()).thenReturn(userEmail);
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
        org.mockito.Mockito.lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void finishShopping_withoutReceipt_shouldCallFinishShoppingOnly() {
        when(shoppingListService.finishShopping(listId, "Kaufland", userEmail))
                .thenReturn(shoppingListDTO);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        var response = shoppingController.finishShopping(
                "Kaufland",
                listId,
                null,
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(listId, response.getBody().getId());
        verify(shoppingListService).finishShopping(listId, "Kaufland", userEmail);
        verify(aiOrchestrationService, org.mockito.Mockito.never()).generateShoppingItems(any(), any(), any(), any());
    }

    @Test
    void finishShopping_withEmptyReceipt_shouldTreatAsNoReceipt() {
        when(shoppingListService.finishShopping(listId, "Kaufland", userEmail))
                .thenReturn(shoppingListDTO);

        MockMultipartFile emptyReceipt = new MockMultipartFile(
                "receipt",
                "receipt.jpg",
                "image/jpeg",
                new byte[0]
        );

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        var response = shoppingController.finishShopping(
                "Kaufland",
                listId,
                emptyReceipt,
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        verify(shoppingListService).finishShopping(listId, "Kaufland", userEmail);
    }

    @Test
    void finishShopping_withReceipt_shouldProcessReceipt() {
        when(shoppingListService.finishShopping(listId, "Kaufland", userEmail))
                .thenReturn(shoppingListDTO);

        AiGenerationResponse aiResponse = new AiGenerationResponse();
        ParsedItemResponse item = new ParsedItemResponse();
        item.setGenericName("Milk");
        item.setSpecificName("Fresh Milk");
        item.setBrand("BrandX");
        item.setCategory("Dairy");
        item.setPrice(new BigDecimal("2.50"));
        aiResponse.setItems(java.util.List.of(item));

        when(aiOrchestrationService.generateShoppingItems(any(), any(), any(), any()))
                .thenReturn(aiResponse);

        ProductCatalog catalogProduct = new ProductCatalog();
        when(catalogService.recordPurchase(any(), any(), any(), any(), any()))
                .thenReturn(catalogProduct);

        MockMultipartFile receipt = new MockMultipartFile(
                "receipt",
                "receipt.jpg",
                "image/jpeg",
                "fake-image-content".getBytes()
        );

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        var response = shoppingController.finishShopping(
                "Kaufland",
                listId,
                receipt,
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        verify(shoppingListService).finishShopping(listId, "Kaufland", userEmail);
        verify(aiOrchestrationService).generateShoppingItems(any(), any(), any(), any());
        verify(catalogService).recordPurchase(any(), any(), any(), any(), any());
        verify(shoppingListService).markReceiptItemPurchased(eq(listId), any(), eq(catalogProduct), eq(userEmail));
    }

    @Test
    void finishShopping_withReceiptAndNullSpecificName_shouldSkipItem() {
        when(shoppingListService.finishShopping(listId, "Kaufland", userEmail))
                .thenReturn(shoppingListDTO);

        AiGenerationResponse aiResponse = new AiGenerationResponse();
        ParsedItemResponse item = new ParsedItemResponse();
        item.setGenericName(null);
        item.setSpecificName(null);
        aiResponse.setItems(java.util.List.of(item));

        when(aiOrchestrationService.generateShoppingItems(any(), any(), any(), any()))
                .thenReturn(aiResponse);

        MockMultipartFile receipt = new MockMultipartFile(
                "receipt",
                "receipt.jpg",
                "image/jpeg",
                "fake-image-content".getBytes()
        );

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        var response = shoppingController.finishShopping(
                "Kaufland",
                listId,
                receipt,
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        verify(shoppingListService).finishShopping(listId, "Kaufland", userEmail);
        verify(aiOrchestrationService).generateShoppingItems(any(), any(), any(), any());
        verify(catalogService, org.mockito.Mockito.never()).recordPurchase(any(), any(), any(), any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
        "primary, fallback, primary",
        "'', fallback, fallback",
        "null, '', null"
    }, nullValues = {"null"})
    void firstNonBlank_parameterized(String primary, String fallback, String expected) throws Exception {
        java.lang.reflect.Method method = ShoppingController.class.getDeclaredMethod("firstNonBlank", String.class, String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(shoppingController, primary, fallback);
        assertEquals(expected, result);
    }
}
