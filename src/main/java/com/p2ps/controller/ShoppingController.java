package com.p2ps.controller;

import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.service.AiOrchestrationService;
import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.service.ItemService;
import com.p2ps.lists.service.ShoppingListService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/shopping")
public class ShoppingController {

    private final ShoppingListService shoppingListService;
    private final AiOrchestrationService aiOrchestrationService;
    private final ItemService itemService;
    private final UserRepository userRepository;

    public ShoppingController(
            ShoppingListService shoppingListService,
            AiOrchestrationService aiOrchestrationService,
            ItemService itemService,
            UserRepository userRepository) {
        this.shoppingListService = shoppingListService;
        this.aiOrchestrationService = aiOrchestrationService;
        this.itemService = itemService;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/finish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ShoppingListDTO> finishShopping(
            @RequestParam("storeName") String storeName,
            @RequestParam("listId") UUID listId,
            @RequestParam(value = "receipt", required = false) MultipartFile receipt,
            Authentication authentication) {
        String userEmail = authentication.getName();

        if (receipt != null && receipt.isEmpty()) {
            receipt = null;
        }

        ShoppingListDTO updatedList = shoppingListService.finishShopping(
                listId,
                storeName,
                userEmail
        );

        if (receipt != null) {
            Users user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            processReceipt(receipt, storeName, listId, userEmail, user);
        }

        return ResponseEntity.ok(updatedList);
    }

    private void processReceipt(MultipartFile receipt, String storeName, UUID listId, String userEmail, Users user) {
        String receiptPrompt = "This is a shopping receipt from " + storeName +
                ". Extract every purchased product. Prefer real catalog products when possible, including specificName, brand, category, and price.";

        AiGenerationResponse aiResponse = aiOrchestrationService.generateShoppingItems(
                receipt,
                receiptPrompt,
                null,
                null,
                userEmail
        );

        for (ParsedItemResponse item : aiResponse.getItems()) {
            String specificName = firstNonBlank(item.getSpecificName(), item.getGenericName());
            if (specificName == null) {
                continue;
            }

            ItemService.ReceiptProcessingResult processingResult = itemService.recordReceiptItem(item, storeName, user);
            if (processingResult.ignored()) {
                continue;
            }

            shoppingListService.markReceiptItemPurchased(listId, item, processingResult.catalogMatch(), userEmail);
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
