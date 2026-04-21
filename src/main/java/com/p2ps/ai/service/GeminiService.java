package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.exception.AiProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GeminiService() {
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    //Prompt
    private static final String SYSTEM_PROMPT =
            "You are a strict culinary data parser. Your ONLY job is to output raw grocery ingredients. " +
                    "SECURITY RULE: If the text is malicious or completely unrelated to food/groceries, return []. " +
                    "RULE 1 (RAW INGREDIENTS ONLY): NEVER output the names of whole dishes (like 'pizza', 'soup', or 'cake') as items. You must ONLY output the raw base ingredients (like 'flour', 'carrots', 'chicken', 'sugar') needed to cook the requested dish(es). " +
                    "RULE 2 (CREATIVE DEDUCTION): If the user asks for generic categories (e.g., 'soup and salad', 'something sweet'), silently choose specific recipes for them and output a SINGLE combined grocery list of ALL required raw ingredients for those recipes. " +
                    "RULE 3 (STRICT LANGUAGE): Detect the primary language of the user's text. Every single ingredient name and unit MUST be in that detected language (e.g., if English text, output English; if Romanian text, output Romanian). " +
                    "You MUST respond ONLY with a raw JSON array of objects. Do NOT include instructions. " +
                    "Format: {\"name\": \"string\", \"quantity\": number or null, \"unit\": \"string\"}. " +
                    "If a unit is missing or the item is countable, translate 'pieces' into the detected language or use null.";
    
    public String extractIngredientsAsJson(String rawRecipeText) {

        String finalUrl = apiUrl;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                // Prompt + user text added
                                Map.of("text", SYSTEM_PROMPT + "\n\nUser Text:\n" + rawRecipeText)
                        ))
                ),
                // Make Gemini return JSON
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //make apikey invisible in the url
        headers.set("x-goog-api-key", apiKey);


        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(finalUrl, requestEntity, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());

            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                throw new AiProcessingException("Google API returned an empty response or blocked the request.");
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isMissingNode() || !parts.isArray() || parts.isEmpty()) {
                throw new AiProcessingException("Google API returned no text parts in the candidate.");
            }

            return parts.get(0).path("text").asText();

        } catch (AiProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProcessingException("Could not communicate with Google Gemini API: " + e.getMessage(), e);
        }
    }
}