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

        // Call Gemini first to get ingredients JSON, then validate before creating any list
        String jsonResult = geminiService.extractIngredientsAsJson(request.getText());

        List<ParsedItemResponse> parsedItems;
        try {
            parsedItems = objectMapper.readValue(
                    jsonResult,
                    new TypeReference<List<ParsedItemResponse>>() {}
            );
        } catch (JsonProcessingException e) {
            throw new AiProcessingException("AI could not return a correctly structured list", e);
        }

        if (parsedItems == null) {
            throw new AiProcessingException("AI returned a null payload instead of a list of items");
        }

        List<ParsedItemResponse> validItems = new ArrayList<>();

        for (ParsedItemResponse aiItem : parsedItems) {
            if (aiItem == null) {
                continue;
            }

            String name = aiItem.getName();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            validItems.add(aiItem);
        }

        if (validItems.isEmpty()) {
            throw new AiProcessingException("AI did not return any valid ingredients to add to a list");
        }

        // Only create the list if we have valid items and there's no target list yet
        if (targetListId == null) {
            String title = (request.getNewListTitle() != null && !request.getNewListTitle().isBlank())
                    ? request.getNewListTitle()
                    : "AI Generated " + java.time.LocalDate.now();
            ShoppingListDTO newList = shoppingListService.createList(title, userEmail);
            targetListId = newList.getId();
        }

        // Save validated items
        for (ParsedItemResponse aiItem : validItems) {
            ItemRequest newItem = new ItemRequest();
            newItem.setName(aiItem.getName().trim());

            String quantityStr = (aiItem.getQuantity() != null ? String.valueOf(aiItem.getQuantity()) : "");
            String unitStr = (aiItem.getUnit() != null ? aiItem.getUnit() : "");
            newItem.setQuantity((quantityStr + " " + unitStr).trim());
            newItem.setCategory("AI Generated");

            itemService.addItemToList(targetListId, newItem, userEmail);
        }

        return validItems;
    }
}