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

    private final AiService aiService;
    private final AiPersistenceService aiPersistenceService;
    private final ObjectMapper objectMapper;

    public AiOrchestrationService(AiService aiService, AiPersistenceService aiPersistenceService, java.util.Optional<ObjectMapper> objectMapper) {
        this.aiService = aiService;
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
        int maxRetries = 2;
        Exception lastException = null;

        for (int i = 0; i <= maxRetries; i++) {
            try {
                String rawResult = aiService.extractIngredientsAsJson(text);
                String jsonResult = extractJson(rawResult);
                List<ParsedItemResponse> parsedItems = objectMapper.readValue(jsonResult, new TypeReference<>() {});
                if (parsedItems != null) return parsedItems;
            } catch (Exception e) {
                lastException = e;
            }
        }
        throw new AiProcessingException("AI could not return a correctly structured list after retries", lastException, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // Multimodal and Gatekeeper Flow
    public AiGenerationResponse generateShoppingItems(MultipartFile image, String text, Double latitude, Double longitude) {
        int maxRetries = 2;
        Exception lastException = null;

        for (int i = 0; i <= maxRetries; i++) {
            try {
                // Receive the generated JSON from AI Service
                String rawResult = aiService.extractFromMultimodal(image, text, latitude, longitude);
                String jsonResult = extractJson(rawResult);

                // Map the JSON to the response object
                AiGenerationResponse response = objectMapper.readValue(jsonResult, AiGenerationResponse.class);

                // Validation
                if (response != null && response.getItems() != null && !response.getItems().isEmpty()) {
                    return response;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }

        throw new AiProcessingException(
                "AI returned an invalid structure after retries. Expected AiGenerationResponse.",
                lastException,
                HttpStatus.UNPROCESSABLE_CONTENT
        );
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) return raw;

        int startBrace = raw.indexOf('{');
        int startBracket = raw.indexOf('[');
        int start = -1;
        int end = -1;

        if (startBrace != -1 && (startBracket == -1 || startBrace < startBracket)) {
            start = startBrace;
            end = raw.lastIndexOf('}');
        } else if (startBracket != -1) {
            start = startBracket;
            end = raw.lastIndexOf(']');
        }

        if (start != -1 && end != -1 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}