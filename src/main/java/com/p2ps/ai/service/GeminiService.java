package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.exception.AiProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final CatalogService catalogService;

    public GeminiService(CatalogService catalogService) {
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);

        this.restTemplate = new RestTemplate(factory);
        this.catalogService = catalogService;
    }

    private static final String SYSTEM_PROMPT =
            "You are a strict multimodal culinary data parser. Your ONLY job is to analyze text and/or images to output a structured grocery list. " +
                    "VISUAL RULES (CRITICAL): " +
                    "1. If the user uploads a photo of a FINISHED DISH, silently deduce the recipe and output the raw ingredients needed to cook it. " +
                    "2. If the user uploads a photo of a FRIDGE or PANTRY, identify the available items. If they ask 'what can I cook?', silently deduce a meal, and output ONLY the missing ingredients they need to buy. If they don't specify, just list all the food items you see. " +
                    "RULE 1 (RAW INGREDIENTS ONLY): NEVER output the names of whole dishes. Output ONLY the raw base ingredients needed. If the user asks for a recipe or meal idea, DO NOT REFUSE. Silently invent the recipe and output the required ingredients. " +
                    "RULE 2 (SMART PROMPTING): Below I will provide you with our 'GLOBAL CATALOG' of popular products. For EVERY ingredient you identify, check if it exists in the Catalog. If yes, map it by copying the exact 'specificName', 'brand', and 'catalogId'. If not found, use a generic name and set 'catalogId' to null. " +
                    "RULE 3 (CATEGORIZATION): Classify the ENTIRE list under ONE 'listType': 'RECIPE' (for cooking), 'FREQUENT' (for household staples/restocks), or 'NORMAL'. Additionally, assign each individual item a logical 'category' (e.g., 'Dairy', 'Produce', 'Cleaning'). " +
                    "RULE 4 (STRICT LANGUAGE): Detect the primary language of the user's input. The 'genericName', 'category', and 'unit' MUST be translated into that exact detected language. " +
                    "You MUST respond ONLY with a raw JSON object. Do NOT include markdown instructions. " +
                    "Format: {\"listType\": \"string\", \"items\": [{\"genericName\": \"string\", \"specificName\": \"string or null\", \"brand\": \"string or null\", \"quantity\": number or null, \"unit\": \"string or null\", \"catalogId\": \"string or null\", \"category\": \"string\"}]}.";

    public String extractFromMultimodal(MultipartFile image, String text) {
        try {
            // Smart prompting: bring Top Products
            List<ProductCatalog> popularProducts = catalogService.getTopPopularProducts();

            // Transform the catalog in a formatted text for AI
            String catalogContext = "=== GLOBAL CATALOG ===\n" + popularProducts.stream()
                    .map(p -> "ID: " + p.getId() + " | Generic Name: " + p.getGenericName() + " | Specific Name: " + p.getSpecificName() + " | Brand: " + (p.getBrand() != null ? p.getBrand() : "N/A"))
                    .collect(Collectors.joining("\n")) + "\n======================";

            // Combine the instruction catalog with user text
            List<Map<String, Object>> parts = new ArrayList<>();
            String finalPrompt = SYSTEM_PROMPT + "\n\n" + catalogContext + "\n\nUser Text:\n" + (text != null ? text : "I want to cook with what's in the photo.");
            parts.add(Map.of("text", finalPrompt));

            if (image != null && !image.isEmpty()) {
                byte[] imageBytes = image.getBytes();
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                String secureMimeType = detectMimeTypeSecurely(imageBytes);
                if (secureMimeType == null) {
                    throw new AiProcessingException("Unsupported or corrupted image format. Only actual JPEG/PNG files are allowed.");
                }

                parts.add(Map.of("inlineData", Map.of(
                        "mimeType", secureMimeType,
                        "data", base64Image
                )));
            }

            // Build request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(Map.of("role", "user", "parts", parts)));

            requestBody.put("generationConfig", Map.of("responseMimeType", "application/json"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            if (rootNode.path("candidates").isMissingNode() || rootNode.path("candidates").isEmpty()) {
                throw new AiProcessingException("Google API returned an empty response.");
            }

            // Return the text directly, guarantees a valid JSON
            return rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

        } catch (Exception e) {
            throw new AiProcessingException("Error during Multimodal AI processing: " + e.getMessage(), e);
        }
    }

    // Legacy method
    public String extractIngredientsAsJson(String rawRecipeText) {
        return extractFromMultimodal(null, rawRecipeText);
    }

    private String detectMimeTypeSecurely(byte[] bytes) {
        if (bytes.length >= 8 &&
                bytes[0] == (byte) 0x89 && bytes[1] == 0x50 &&
                bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 2 &&
                bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        return null;
    }
}