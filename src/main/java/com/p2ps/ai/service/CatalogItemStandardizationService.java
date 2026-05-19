package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.exception.AiProcessingException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogItemStandardizationService {

    private static final String SYSTEM_PROMPT = """
    You standardize grocery product names for a Romanian shopping app catalog.
    Return ONLY one valid JSON object with this exact schema:
    {"cleanName":"string","category":"string","brand":"string or null"}
    Rules:
            - cleanName must be a concise, consumer-friendly normalized product name IN ROMANIAN (e.g., if user inputs "ardei", return "Ardei", NOT "Bell peppers").
            - category must be a short grocery category label IN ROMANIAN (choose from: "Fructe și Legume", "Lactate și Ouă", "Carne", "Băcănie", "Dulciuri", "Băuturi", "Îngrijire Personală", "Curățenie", "Altele").
            - brand must be null when the raw input does not clearly imply one.
            - Do not include explanations, markdown formatting (like ```json), or extra keys.
            """;

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public CatalogItemStandardizationService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    // Schimbăm semnătura metodei
    public CatalogStandardizationResult standardize(String rawName, String userBrand, String userCategory, java.math.BigDecimal userPrice) {
        if (rawName == null || rawName.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw product name must not be blank");
        }

        // AICI ESTE NOUL TĂU PROMPT DINAMIC 🔥
        String contextPrompt = String.format("""
                Raw user-entered product: %s
                Hint - User provided brand: %s
                Hint - User provided category: %s
                Hint - User provided price: %s
                
                Please use the hints above if they are valid, correct any typos, and return the final standardized JSON.
                """,
                rawName.trim(),
                (userBrand != null && !userBrand.isBlank() ? userBrand : "Unknown"),
                (userCategory != null && !userCategory.isBlank() ? userCategory : "Unknown"),
                (userPrice != null ? userPrice.toString() : "Unknown")
        );

        String combinedPrompt = SYSTEM_PROMPT + "\n\n" + contextPrompt;

        List<AiMessage> messages = List.of(
                new AiMessage("user", List.of(new AiMessage.TextPart(combinedPrompt)))
        );

        // ... restul metodei rămâne exact la fel (aiClient.generateResponse...)

        AiMessage response = aiClient.generateResponse(messages, List.of());
        String rawResponse = response.parts().stream()
                .filter(AiMessage.TextPart.class::isInstance)
                .map(AiMessage.TextPart.class::cast)
                .map(AiMessage.TextPart::text)
                .reduce("", (left, right) -> left + right);

        if (rawResponse.isBlank()) {
            throw new AiProcessingException("AI returned an empty catalog standardization response.");
        }

        try {
            CatalogStandardizationResult parsed = objectMapper.readValue(
                    extractJsonObject(rawResponse),
                    CatalogStandardizationResult.class
            );
            return new CatalogStandardizationResult(
                    requireText(parsed.cleanName(), "cleanName"),
                    requireText(parsed.category(), "category"),
                    normalizeNullable(parsed.brand())
            );
        } catch (AiProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiProcessingException("Failed to parse AI catalog standardization response.", exception);
        }
    }

    private String extractJsonObject(String rawResponse) {
        int start = rawResponse.indexOf('{');
        int end = rawResponse.lastIndexOf('}');
        if (start == -1 || end <= start) {
            throw new AiProcessingException("AI response did not contain a valid JSON object.");
        }
        return rawResponse.substring(start, end + 1);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new AiProcessingException("AI catalog standardization response is missing field: " + fieldName);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
