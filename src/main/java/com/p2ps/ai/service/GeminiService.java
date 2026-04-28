package com.p2ps.ai.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.service.StoreMatchingEngine;
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
    private final StoreMatchingEngine storeMatchingEngine;

    public GeminiService(CatalogService catalogService, StoreMatchingEngine storeMatchingEngine) {
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);

        this.restTemplate = new RestTemplate(factory);
        this.catalogService = catalogService;
        this.storeMatchingEngine = storeMatchingEngine;
    }

    private static final String SYSTEM_PROMPT =
            "You are a strict multimodal culinary data parser. Your ONLY job is to analyze text and/or images to output a structured grocery list. " +
            "VISUAL RULES (CRITICAL): " +
            "1. If the user uploads a photo of a FINISHED DISH, deduce the recipe and output raw ingredients. " +
            "2. If the user uploads a photo of a FRIDGE/PANTRY, identify items and deduce missing ingredients if asked. " +
            "3. If the user uploads a PHOTO of a RECEIPT, extract all products, brands, and quantities. " +
            "RULE 1 (DYNAMIC SEARCH): You have access to tools to search our product catalog and find nearby stores. ALWAYS search the catalog for generic ingredients to map them to real-world products. " +
            "RULE 2 (LOCATION AWARENESS): If user coordinates are provided, use the 'find_optimal_store' tool to recommend the best place to shop. " +
            "RULE 3 (TIERED CATEGORIZATION): Classify the list as 'RECIPE', 'FREQUENT', or 'NORMAL'. For 'FREQUENT', assign categories (Dairy, Produce, etc.). " +
            "Format: {\"listType\": \"string\", \"suggestedStore\": \"string or null\", \"items\": [{\"genericName\": \"string\", \"specificName\": \"string or null\", \"brand\": \"string or null\", \"quantity\": number or null, \"unit\": \"string or null\", \"catalogId\": \"string or null\", \"category\": \"string\"}]}.";

    public String extractFromMultimodal(MultipartFile image, String text, Double latitude, Double longitude) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            List<Map<String, Object>> userParts = buildRequestParts(image, text);
            messages.add(Map.of("role", "user", "parts", userParts));

            int maxIterations = 5;
            for (int i = 0; i < maxIterations; i++) {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("contents", messages);
                requestBody.put("tools", List.of(Map.of("function_declarations", getToolDefinitions())));
                requestBody.put("generationConfig", Map.of("responseMimeType", "application/json"));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("x-goog-api-key", apiKey);

                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

                JsonNode rootNode = objectMapper.readTree(response.getBody());
                JsonNode candidate = rootNode.path("candidates").get(0);
                JsonNode content = candidate.path("content");
                JsonNode partsNode = content.path("parts");

                messages.add(Map.of("role", "model", "parts", objectMapper.convertValue(partsNode, List.class)));

                JsonNode functionCall = null;
                for (JsonNode part : partsNode) {
                    if (part.has("functionCall")) {
                        functionCall = part.get("functionCall");
                        break;
                    }
                }

                if (functionCall != null) {
                    String funcName = functionCall.get("name").asText();
                    Map<String, Object> args = objectMapper.convertValue(functionCall.get("args"), Map.class);
                    Object result = executeTool(funcName, args, latitude, longitude);

                    Map<String, Object> toolResponsePart = Map.of(
                            "functionResponse", Map.of(
                                    "name", funcName,
                                    "response", Map.of("content", result)
                            )
                    );
                    messages.add(Map.of("role", "function", "parts", List.of(toolResponsePart)));
                } else {
                    return parseTextResponse(response.getBody());
                }
            }
            throw new AiProcessingException("AI reached maximum tool-calling iterations.");
        } catch (AiProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProcessingException("Error during Tool-Calling AI processing: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> getToolDefinitions() {
        return List.of(
                Map.of(
                        "name", "search_catalog",
                        "description", "Search the global product catalog for a specific item or keyword to find brands and real-world names.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "STRING", "description", "The product name or keyword to search for.")
                                ),
                                "required", List.of("keyword")
                        )
                ),
                Map.of(
                        "name", "find_optimal_store",
                        "description", "Find the best nearby store based on user location and item availability.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "radius_meters", Map.of("type", "INTEGER", "description", "Search radius in meters (default 5000)."),
                                        "item_ids", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "description", "List of catalog product UUIDs to match in inventory.")
                                ),
                                "required", List.of("item_ids")
                        )
                )
        );
    }

    private Object executeTool(String name, Map<String, Object> args, Double lat, Double lng) {
        if ("search_catalog".equals(name)) {
            String keyword = (String) args.get("keyword");
            return catalogService.searchProductsByName(keyword);
        }
        if ("find_optimal_store".equals(name)) {
            if (lat == null || lng == null) return "User location not provided. Cannot search stores.";
            int radius = (args.get("radius_meters") != null) ? (Integer) args.get("radius_meters") : 5000;
            List<String> idStrings = (List<String>) args.get("item_ids");
            List<UUID> itemIds = idStrings.stream().map(UUID::fromString).collect(Collectors.toList());
            return storeMatchingEngine.findOptimalStore(lat, lng, radius, itemIds);
        }
        return "Unknown tool";
    }


    private List<Map<String, Object>> buildRequestParts(MultipartFile image, String text) throws IOException {
        List<Map<String, Object>> parts = new ArrayList<>();
        String fallbackText = (image != null && !image.isEmpty())
                ? "I want to cook with what's in the photo."
                : "Please analyze the text below.";

        String finalPrompt = SYSTEM_PROMPT + "\n\nUser Text:\n" +
                (text != null && !text.trim().isEmpty() ? text : fallbackText);
        parts.add(Map.of("text", finalPrompt));

        if (image != null && !image.isEmpty()) {
            byte[] imageBytes = image.getBytes();
            String secureMimeType = detectMimeTypeSecurely(imageBytes);
            if (secureMimeType == null) {
                throw new AiProcessingException("Unsupported or corrupted image format. Only actual JPEG/PNG files are allowed.");
            }

            parts.add(Map.of("inlineData", Map.of(
                    "mimeType", secureMimeType,
                    "data", Base64.getEncoder().encodeToString(imageBytes)
            )));
        }
        return parts;
    }

    private String parseTextResponse(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode candidates = rootNode.path("candidates");
        if (candidates.isMissingNode() || candidates.isEmpty()) {
            throw new AiProcessingException("Google API returned an empty response.");
        }

        JsonNode responseParts = candidates.get(0).path("content").path("parts");
        if (responseParts.isMissingNode() || responseParts.isEmpty()) {
            throw new AiProcessingException("Google API returned candidate without text parts.");
        }

        JsonNode textNode = responseParts.get(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().trim().isEmpty()) {
            throw new AiProcessingException("Google API returned an empty text response.");
        }

        return textNode.asText();
    }

    public String extractIngredientsAsJson(String rawRecipeText) {
        return extractFromMultimodal(null, rawRecipeText, null, null);
    }

    private String detectMimeTypeSecurely(byte[] bytes) {
        try (InputStream is = new ByteArrayInputStream(bytes);
             ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;

            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase();
                if (format.equals("png")) return "image/png";
                if (format.equals("jpeg") || format.equals("jpg")) return "image/jpeg";
            } finally {
                reader.dispose();
            }
        } catch (IOException _) {
            // Return null if parsing fails
        }
        return null;
    }
}