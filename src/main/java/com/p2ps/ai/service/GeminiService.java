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
            "3. If the user uploads a PHOTO of a RECEIPT, extract all products, brands, and quantities accurately. " +
            "RULE 1 (RAW INGREDIENTS ONLY): NEVER output the names of whole dishes. Output ONLY the raw base ingredients needed. " +
            "RULE 2 (STRICT CATALOG MATCHING): Below is our 'GLOBAL CATALOG'. For EVERY ingredient you need, you MUST search this catalog first. If the ingredient exists in the catalog (e.g., you need 'milk' and 'Lapte Zuzu' is in the catalog), you MUST replace the generic item with the exact catalog item. Map it by copying the exact 'specificName', 'brand', and 'catalogId' from the catalog. This ensures 'Community-Driven Context Awareness' by mapping generic terms to real-world popular products. " +
            "RULE 3 (TIERED CATEGORIZATION): Classify the ENTIRE list under ONE 'listType': 'RECIPE' (for cooking), 'FREQUENT' (for recurring favorites/essentials), or 'NORMAL' (standard ad-hoc list). " +
            "For 'FREQUENT' lists, you MUST assign each item a specific 'category' (e.g., 'Dairy', 'Produce', 'Cleaning Supplies', 'Bakery') to support nested sub-categories. " +
            "RULE 4 (STRICT LANGUAGE): Detect the primary language of the user's input. The 'genericName', 'category', and 'unit' MUST be translated into that exact detected language. " +
            "You MUST respond ONLY with a raw JSON object. Do NOT include markdown instructions. " +
            "Format: {\"listType\": \"string\", \"items\": [{\"genericName\": \"string\", \"specificName\": \"string or null\", \"brand\": \"string or null\", \"quantity\": number or null, \"unit\": \"string or null\", \"catalogId\": \"string or null\", \"category\": \"string\"}]}.";

    public String extractFromMultimodal(MultipartFile image, String text) {
        try {
            List<ProductCatalog> popularProducts = catalogService.getTopPopularProducts();
            String catalogContext = buildCatalogContext(popularProducts);
            List<Map<String, Object>> parts = buildRequestParts(image, text, catalogContext);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(Map.of("role", "user", "parts", parts)));
            requestBody.put("generationConfig", Map.of("responseMimeType", "application/json"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            return parseTextResponse(response.getBody());
        } catch (AiProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProcessingException("Error during Multimodal AI processing: " + e.getMessage(), e);
        }
    }

    private String buildCatalogContext(List<ProductCatalog> popularProducts) {
        return "=== GLOBAL CATALOG ===\n" + popularProducts.stream()
                .map(p -> "ID: " + p.getId() + " | Generic Name: " + p.getGenericName() + " | Specific Name: " + p.getSpecificName() + " | Brand: " + (p.getBrand() != null ? p.getBrand() : "N/A"))
                .collect(Collectors.joining("\n")) + "\n======================";
    }

    private List<Map<String, Object>> buildRequestParts(MultipartFile image, String text, String catalogContext) throws IOException {
        List<Map<String, Object>> parts = new ArrayList<>();
        String fallbackText = (image != null && !image.isEmpty())
                ? "I want to cook with what's in the photo."
                : "Please analyze the text below.";

        String finalPrompt = SYSTEM_PROMPT + "\n\n" + catalogContext + "\n\nUser Text:\n" +
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
        return extractFromMultimodal(null, rawRecipeText);
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