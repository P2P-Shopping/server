package com.p2ps.controller;

import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.service.AiOrchestrationService;
import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.lists.service.ItemService;
import com.p2ps.lists.service.ShoppingListService;
import com.p2ps.shopping.dto.ShoppingSessionDTO;
import com.p2ps.shopping.dto.StartShoppingRequest;
import com.p2ps.shopping.model.ShoppingSessionStatus;
import com.p2ps.shopping.service.ShoppingSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingControllerTest {

    @Mock
    private ShoppingSessionService shoppingSessionService;
    @Mock
    private AiOrchestrationService aiOrchestrationService;
    @Mock
    private ItemService itemService;
    @Mock
    private ShoppingListService shoppingListService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private ShoppingController shoppingController;

    @Test
    void startShopping_delegatesToService() {
        String email = "user@example.com";
        StartShoppingRequest request = new StartShoppingRequest();
        request.setListId(UUID.randomUUID());

        ShoppingSessionDTO dto = new ShoppingSessionDTO();
        dto.setSessionId(UUID.randomUUID());
        dto.setStatus(ShoppingSessionStatus.ACTIVE);

        when(authentication.getName()).thenReturn(email);
        when(shoppingSessionService.startShopping(request, email)).thenReturn(dto);

        ResponseEntity<ShoppingSessionDTO> response = shoppingController.startShopping(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void getActiveSession_whenPresent_returns200() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";
        ShoppingSessionDTO dto = new ShoppingSessionDTO();
        dto.setSessionId(UUID.randomUUID());

        when(authentication.getName()).thenReturn(email);
        when(shoppingSessionService.getActiveSession(listId, email)).thenReturn(Optional.of(dto));

        ResponseEntity<ShoppingSessionDTO> response = shoppingController.getActiveSession(listId, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void getActiveSession_whenMissing_returns204() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";

        when(authentication.getName()).thenReturn(email);
        when(shoppingSessionService.getActiveSession(listId, email)).thenReturn(Optional.empty());

        ResponseEntity<ShoppingSessionDTO> response = shoppingController.getActiveSession(listId, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void finishShopping_withReceipt_processesItemsAndFinishesSession() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";
        MockMultipartFile receipt = new MockMultipartFile("receipt", "receipt.jpg", "image/jpeg", new byte[]{1, 2});

        Users user = new Users(email, "pass", "Test", "User");
        user.setId(1);

        ShoppingSessionDTO active = new ShoppingSessionDTO();
        active.setStoreName("Mega");

        ParsedItemResponse itemWithName = new ParsedItemResponse();
        itemWithName.setSpecificName("Lapte Zuzu");
        itemWithName.setPrice(new BigDecimal("9.99"));

        ParsedItemResponse itemWithoutName = new ParsedItemResponse();
        itemWithoutName.setSpecificName(" ");
        itemWithoutName.setGenericName(" ");

        AiGenerationResponse aiResponse = new AiGenerationResponse();
        aiResponse.setItems(List.of(itemWithName, itemWithoutName));

        ProductCatalog catalog = new ProductCatalog();
        catalog.setId(UUID.randomUUID());

        ShoppingSessionDTO finished = new ShoppingSessionDTO();
        finished.setSessionId(UUID.randomUUID());
        finished.setStatus(ShoppingSessionStatus.FINISHED);
        finished.setStoreName("Mega");

        when(authentication.getName()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(shoppingSessionService.getActiveSession(listId, email)).thenReturn(Optional.of(active));
        when(aiOrchestrationService.generateShoppingItems(any(), any(), eq(null), eq(null), eq(email))).thenReturn(aiResponse);
        when(itemService.recordReceiptItem(itemWithName, listId, user))
                .thenReturn(new ItemService.ReceiptProcessingResult(false, catalog));
        when(itemService.recordReceiptItem(itemWithoutName, listId, user))
                .thenReturn(ItemService.ReceiptProcessingResult.createIgnored());
        when(shoppingSessionService.finishShopping(listId, email)).thenReturn(finished);

        ResponseEntity<ShoppingSessionDTO> response = shoppingController.finishShopping(listId, receipt, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(finished, response.getBody());
        verify(shoppingListService).markReceiptItemPurchased(listId, itemWithName, catalog, email);
        verify(shoppingListService, never()).markReceiptItemPurchased(listId, itemWithoutName, null, email);
    }

    @Test
    void finishShopping_withEmptyReceipt_skipsAiPipeline() {
        UUID listId = UUID.randomUUID();
        String email = "user@example.com";
        MockMultipartFile emptyReceipt = new MockMultipartFile("receipt", "receipt.jpg", "image/jpeg", new byte[]{});

        ShoppingSessionDTO finished = new ShoppingSessionDTO();
        finished.setSessionId(UUID.randomUUID());
        finished.setStatus(ShoppingSessionStatus.FINISHED);

        when(authentication.getName()).thenReturn(email);
        when(shoppingSessionService.finishShopping(listId, email)).thenReturn(finished);

        ResponseEntity<ShoppingSessionDTO> response = shoppingController.finishShopping(listId, emptyReceipt, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(userRepository, never()).findByEmail(any());
        verify(aiOrchestrationService, never()).generateShoppingItems(any(), any(), any(), any(), any());
        verify(itemService, never()).recordReceiptItem(any(), any(UUID.class), any(Users.class));
    }
}
