package com.p2ps.ai.service;

import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.ai.core.AiTool;
import com.p2ps.ai.core.ToolRegistry;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.service.StoreMatchingEngine;
import com.p2ps.exception.AiProcessingException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {

    private final AiClient aiClient;
    private final ToolRegistry toolRegistry;
    private final CatalogService catalogService;
    private final StoreMatchingEngine storeMatchingEngine;

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

    public AiService(AiClient aiClient, CatalogService catalogService, StoreMatchingEngine storeMatchingEngine) {
        this.aiClient = aiClient;
        this.toolRegistry = new ToolRegistry();
        this.catalogService = catalogService;
        this.storeMatchingEngine = storeMatchingEngine;
    }

    @PostConstruct
    public void initTools() {
        toolRegistry.register(new AiTool(
                "search_catalog",
                "Search the global product catalog for a specific item or keyword to find brands and real-world names.",
                Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "keyword", Map.of("type", "STRING", "description", "The product name or keyword to search for.")
                        ),
                        "required", List.of("keyword")
                ),
                (args, context) -> {
                    String keyword = (String) args.get("keyword");
                    return catalogService.searchProductsByName(keyword);
                }
        ));

        toolRegistry.register(new AiTool(
                "find_optimal_store",
                "Find the best nearby store based on user location and item availability.",
                Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "radius_meters", Map.of("type", "INTEGER", "description", "Search radius in meters (default 5000)."),
                                "item_ids", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "description", "List of catalog product UUIDs to match in inventory.")
                        ),
                        "required", List.of("item_ids")
                ),
                (args, context) -> {
                    Double lat = (Double) context.get("latitude");
                    Double lng = (Double) context.get("longitude");
                    if (lat == null || lng == null) return "User location not provided. Cannot search stores.";
                    int radius = (args.get("radius_meters") != null) ? (Integer) args.get("radius_meters") : 5000;
                    List<String> idStrings = (List<String>) args.get("item_ids");
                    List<UUID> itemIds = idStrings.stream().map(UUID::fromString).collect(Collectors.toList());
                    return storeMatchingEngine.findOptimalStore(lat, lng, radius, itemIds);
                }
        ));
    }

    public String extractFromMultimodal(MultipartFile image, String text, Double latitude, Double longitude) {
        List<AiMessage> messages = new ArrayList<>();
        List<AiMessage.Part> userParts = new ArrayList<>();

        String fallbackText = (image != null && !image.isEmpty())
                ? "I want to cook with what's in the photo."
                : "Please analyze the text below.";

        String finalPrompt = SYSTEM_PROMPT + "\n\nUser Text:\n" +
                (text != null && !text.trim().isEmpty() ? text : fallbackText);
        userParts.add(new AiMessage.TextPart(finalPrompt));

        if (image != null && !image.isEmpty()) {
            try {
                byte[] imageBytes = image.getBytes();
                String mimeType = detectMimeTypeSecurely(imageBytes);
                if (mimeType == null) {
                    throw new AiProcessingException("Unsupported or corrupted image format.");
                }
                userParts.add(new AiMessage.ImagePart(imageBytes, mimeType));
            } catch (IOException e) {
                throw new AiProcessingException("Error reading image: " + e.getMessage());
            }
        }

        messages.add(new AiMessage("user", userParts));

        Map<String, Object> context = new HashMap<>();
        context.put("latitude", latitude);
        context.put("longitude", longitude);

        while (true) {
            AiMessage response = aiClient.generateResponse(messages, toolRegistry.getAvailableTools());
            messages.add(response);

            List<AiMessage.ToolCallPart> toolCalls = response.parts().stream()
                    .filter(p -> p instanceof AiMessage.ToolCallPart)
                    .map(p -> (AiMessage.ToolCallPart) p)
                    .collect(Collectors.toList());

            if (!toolCalls.isEmpty()) {
                List<AiMessage.Part> toolResponses = new ArrayList<>();
                for (AiMessage.ToolCallPart call : toolCalls) {
                    Object result = toolRegistry.executeTool(call.name(), call.arguments(), context);
                    toolResponses.add(new AiMessage.ToolResponsePart(call.name(), result));
                }
                messages.add(new AiMessage("function", toolResponses));
            } else {
                return response.parts().stream()
                        .filter(p -> p instanceof AiMessage.TextPart)
                        .map(p -> ((AiMessage.TextPart) p).text())
                        .collect(Collectors.joining("\n"));
            }
        }

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
        } catch (IOException _) {}
        return null;
    }
}
