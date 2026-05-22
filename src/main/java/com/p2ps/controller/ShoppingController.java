package com.p2ps.controller;

import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.service.AiOrchestrationService;
import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.lists.service.ItemService;
import com.p2ps.lists.service.ShoppingListService;
import com.p2ps.shopping.dto.ShoppingSessionDTO;
import com.p2ps.shopping.dto.StartShoppingRequest;
import com.p2ps.shopping.service.ShoppingSessionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/shopping")
public class ShoppingController {

    private final ShoppingSessionService shoppingSessionService;
    private final AiOrchestrationService aiOrchestrationService;
    private final ItemService itemService;
    private final ShoppingListService shoppingListService;
    private final UserRepository userRepository;

    public ShoppingController(
            ShoppingSessionService shoppingSessionService,
            AiOrchestrationService aiOrchestrationService,
            ItemService itemService,
            ShoppingListService shoppingListService,
            UserRepository userRepository) {
        this.shoppingSessionService = shoppingSessionService;
        this.aiOrchestrationService = aiOrchestrationService;
        this.itemService = itemService;
        this.shoppingListService = shoppingListService;
        this.userRepository = userRepository;
    }

    @PostMapping("/start")
    public ResponseEntity<ShoppingSessionDTO> startShopping(
            @RequestBody StartShoppingRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(shoppingSessionService.startShopping(request, authentication.getName()));
    }

    @GetMapping("/session")
    public ResponseEntity<ShoppingSessionDTO> getActiveSession(
            @RequestParam("listId") UUID listId,
            Authentication authentication) {
        Optional<ShoppingSessionDTO> session = shoppingSessionService.getActiveSession(listId, authentication.getName());
        return session.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.<ShoppingSessionDTO>noContent().build());
    }

    @PostMapping(value = "/finish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ShoppingSessionDTO> finishShopping(
            @RequestParam("listId") UUID listId,
            @RequestParam(value = "receipt", required = false) MultipartFile receipt,
            Authentication authentication) {
        String userEmail = authentication.getName();

        if (receipt != null && receipt.isEmpty()) {
            receipt = null;
        }

        if (receipt != null) {
            Users user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            String storeName = shoppingSessionService.getActiveSession(listId, userEmail)
                    .map(ShoppingSessionDTO::getStoreName)
                    .orElse("this store");
            processReceipt(receipt, storeName, listId, userEmail, user);
        }

        ShoppingSessionDTO completedSession = shoppingSessionService.finishShopping(listId, userEmail);
        return ResponseEntity.ok(completedSession);
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
            ItemService.ReceiptProcessingResult processingResult = itemService.recordReceiptItem(item, listId, user);
            boolean shouldMarkPurchased = specificName != null && !processingResult.ignored();
            if (shouldMarkPurchased) {
                shoppingListService.markReceiptItemPurchased(listId, item, processingResult.catalogMatch(), userEmail);
            }
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
