package com.p2ps.ai.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.service.GeminiService;
import com.p2ps.exception.AiProcessingException;

import com.p2ps.lists.dto.CreateListRequest;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.service.ShoppingListService;
import com.p2ps.lists.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService geminiService;
    private final ShoppingListService shoppingListService;
    private final ItemService itemService;
    private final ObjectMapper objectMapper;

    public AiController(GeminiService geminiService, ItemService itemService, ShoppingListService shoppingListService) {
        this.geminiService = geminiService;
        this.itemService = itemService;
        this.shoppingListService = shoppingListService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/recipe-to-list")
    public ResponseEntity<List<ParsedItemResponse>> parseRecipe(@Valid @RequestBody RecipeRequest request, Principal principal) {

        String userEmail = principal.getName();
        UUID targetListId = request.getListId();

        if (targetListId == null) {
            String title = (request.getNewListTitle() != null && !request.getNewListTitle().isBlank())
                    ? request.getNewListTitle()
                    : "AI Generated " + java.time.LocalDate.now();
            ShoppingListDTO newList = shoppingListService.createList(title, userEmail);
            targetListId = newList.getId();
        }

        String jsonResult = geminiService.extractIngredientsAsJson(request.getText());

        try {
            List<ParsedItemResponse> parsedItems = objectMapper.readValue(
                    jsonResult,
                    new TypeReference<List<ParsedItemResponse>>() {}
            );

            for (ParsedItemResponse aiItem : parsedItems) {

                ItemRequest newItem = new ItemRequest();
                newItem.setName(aiItem.getName());

                // Concatenate quantity and unit of measure for database (ex: "2.5" + " kg" = "2.5 kg")
                String quantityStr = (aiItem.getQuantity() != null ? String.valueOf(aiItem.getQuantity()) : "");
                String unitStr = (aiItem.getUnit() != null ? aiItem.getUnit() : "");
                newItem.setQuantity((quantityStr + " " + unitStr).trim());

                newItem.setCategory("AI Generated");

                itemService.addItemToList(targetListId, newItem, userEmail);
            }

            return ResponseEntity.ok(parsedItems);

        } catch (JsonProcessingException e) {
            throw new AiProcessingException("AI could not return a correctly structured list", e);
        }
    }
}