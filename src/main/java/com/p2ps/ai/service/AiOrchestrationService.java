package com.p2ps.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.exception.AiProcessingException;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.service.ItemService;
import com.p2ps.lists.service.ShoppingListService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiOrchestrationService {

    private final GeminiService geminiService;
    private final ShoppingListService shoppingListService;
    private final ItemService itemService;
    private final ObjectMapper objectMapper;

    public AiOrchestrationService(GeminiService geminiService, ShoppingListService shoppingListService, ItemService itemService) {
        this.geminiService = geminiService;
        this.shoppingListService = shoppingListService;
        this.itemService = itemService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public List<ParsedItemResponse> processRecipeAndPopulateList(RecipeRequest request, String userEmail) {
        UUID targetListId = request.getListId();

        // List creation if needed
        if (targetListId == null) {
            String title = (request.getNewListTitle() != null && !request.getNewListTitle().isBlank())
                    ? request.getNewListTitle()
                    : "AI Generated " + java.time.LocalDate.now();
            ShoppingListDTO newList = shoppingListService.createList(title, userEmail);
            targetListId = newList.getId();
        }

        // Gemini call
        String jsonResult = geminiService.extractIngredientsAsJson(request.getText());

       // Save items
        try {
            List<ParsedItemResponse> parsedItems = objectMapper.readValue(
                    jsonResult,
                    new TypeReference<List<ParsedItemResponse>>() {}
            );

            List<ParsedItemResponse> validItems = new ArrayList<>();

            for (ParsedItemResponse aiItem : parsedItems) {
                if (aiItem.getName() == null || aiItem.getName().trim().isEmpty()) {
                    continue;
                }

                ItemRequest newItem = new ItemRequest();
                newItem.setName(aiItem.getName().trim());

                String quantityStr = (aiItem.getQuantity() != null ? String.valueOf(aiItem.getQuantity()) : "");
                String unitStr = (aiItem.getUnit() != null ? aiItem.getUnit() : "");
                newItem.setQuantity((quantityStr + " " + unitStr).trim());
                newItem.setCategory("AI Generated");

                itemService.addItemToList(targetListId, newItem, userEmail);
                validItems.add(aiItem);
            }

            return validItems;

        } catch (JsonProcessingException e) {
            throw new AiProcessingException("AI could not return a correctly structured list", e);
        }
    }
}