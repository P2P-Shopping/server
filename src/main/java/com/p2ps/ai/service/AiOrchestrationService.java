package com.p2ps.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.exception.AiProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiOrchestrationService {

    private final GeminiService geminiService;
    private final AiPersistenceService aiPersistenceService;
    private final ObjectMapper objectMapper;

    public AiOrchestrationService(GeminiService geminiService, AiPersistenceService aiPersistenceService, java.util.Optional<ObjectMapper> objectMapper) {
        this.geminiService = geminiService;
        this.aiPersistenceService = aiPersistenceService;
        this.objectMapper = objectMapper.orElseGet(ObjectMapper::new);
    }

    //Legacy parsing method
    public List<ParsedItemResponse> processRecipeAndPopulateList(RecipeRequest request, String userEmail) {
        List<ParsedItemResponse> parsedItems = parseIngredientsFromText(request.getText());

        List<ParsedItemResponse> validItems = new ArrayList<>();
        for (ParsedItemResponse aiItem : parsedItems) {
            if (aiItem != null && aiItem.getGenericName() != null && !aiItem.getGenericName().trim().isEmpty()) {
                validItems.add(aiItem);
            }
        }

        if (validItems.isEmpty()) {
            throw new AiProcessingException("AI did not return any valid ingredients to add to a list", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        aiPersistenceService.createListAndPopulateItems(request.getListId(), request.getNewListTitle(), validItems, userEmail);

        return validItems;
    }

    private List<ParsedItemResponse> parseIngredientsFromText(String text) {
        String jsonResult = geminiService.extractIngredientsAsJson(text);

        List<ParsedItemResponse> parsedItems;
        try {
            parsedItems = objectMapper.readValue(jsonResult, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new AiProcessingException("AI could not return a correctly structured list", e, HttpStatus.UNPROCESSABLE_CONTENT);
        }

        if (parsedItems == null) {
            throw new AiProcessingException("AI returned a null payload instead of a list of items", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        return parsedItems;
    }

    // Multimodal and Gatekeeper Flow
    public AiGenerationResponse generateShoppingItems(MultipartFile image, String text) {
        // Receive the generated JSON from Gemini
        String jsonResult = geminiService.extractFromMultimodal(image, text);

        // Map the JSON to the response object
        AiGenerationResponse response;
        try {
            response = objectMapper.readValue(jsonResult, AiGenerationResponse.class);
        } catch (JsonProcessingException e) {
            throw new AiProcessingException(
                    "AI returned an invalid structure. Expected AiGenerationResponse.",
                    e,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        // Validation
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            throw new AiProcessingException("AI did not return any valid items.", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        return response;
    }
}