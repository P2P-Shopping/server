package com.p2ps.ai.service;

import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.ai.core.AiTool;
import com.p2ps.ai.core.ToolRegistry;
import com.p2ps.exception.AiProcessingException;
import com.p2ps.service.StoreMatchingEngine;
import com.p2ps.catalog.service.CatalogService;
import jakarta.annotation.PostConstruct;
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
    private final StoreMatchingEngine storeMatchingEngine;

    private static final String SYSTEM_PROMPT =
            "You are a strict multimodal culinary data parser. Your ONLY job is to analyze text and/or images to output a structured grocery list. " +
                    "LANGUAGE RULE (CRITICAL): Preserve the user's language for genericName, category, and any non-brand text. If the user writes in Romanian, respond in Romanian. " +
                    "VISUAL RULES (CRITICAL): " +
                    "1. If the user uploads a photo of a FINISHED DISH, deduce the recipe and output raw ingredients. " +
                    "2. If the user uploads a photo of a FRIDGE/PANTRY, identify items and deduce missing ingredients if asked. " +
                    "3. If the user uploads a PHOTO of a RECEIPT, extract all products, brands, and quantities. " +
                    "RULE 1 (DYNAMIC SEARCH): You have access to tools to search our product catalog and find nearby stores. ALWAYS search the catalog for generic ingredients to map them to real-world products. " +
                    "RULE 1A (CATALOG MAPPING): If catalog results exist, prefer a real catalog product. Fill specificName, brand, and catalogId from the best matching catalog entry. Only leave catalogId null if no relevant catalog product exists. " +
                    "RULE 1B (RECEIPT PRICE): For receipt photos, also extract the product price when visible and include it in the output. " +
                    "RULE 2 (LOCATION AWARENESS): If user coordinates are provided, use the 'find_optimal_store' tool to recommend the best place to shop. " +
                    "RULE 3 (TIERED CATEGORIZATION): Classify the list as 'RECIPE', 'FREQUENT', or 'CART'. " +
                    "RECIPE LOGIC: If the user describes a dish, dessert, meal, or recipe idea (e.g., negresa, clatite, ciorba, pasta, cake), classify it as 'RECIPE' even if the word 'recipe' is not used. " +
                    "CRITICAL CATEGORY RULE: The 'category' field MUST be chosen EXACTLY from this strict list: [Fructe și Legume, Lactate și Ouă, Carne, Băcănie, Dulciuri, Curățenie, Altele]. DO NOT invent categories! " +
                    "Format: {\"listType\": \"string\", \"suggestedStore\": \"string or null\", \"items\": [{\"genericName\": \"string\", \"specificName\": \"string or null\", \"brand\": \"string or null\", \"quantity\": number or null, \"unit\": \"string or null\", \"catalogId\": \"string or null\", \"category\": \"string\", \"price\": number or null}]}.";
    private static final String FINAL_JSON_PROMPT =
            "Return ONLY valid JSON matching exactly this schema and nothing else: " +
                    "{\"listType\":\"RECIPE|FREQUENT|CART\",\"suggestedStore\":\"string or null\",\"items\":[{\"genericName\":\"string\",\"specificName\":\"string or null\",\"brand\":\"string or null\",\"quantity\":number or null,\"unit\":\"string or null\",\"catalogId\":\"string or null\",\"category\":\"string\",\"price\":number or null}]}. " +
                    "If catalog tool results were found, copy the chosen product's specificName, brand, and catalogId into the JSON. Preserve the user's language for genericName and category. Remember to use the STRICT category list. Do not add markdown, explanations, or prose.";
    private static final String DESCRIPTION = "description";
    private static final String RADIUS_METERS = "radius_meters";
    private static final String ITEM_IDS = "item_ids";

    public AiService(AiClient aiClient, StoreMatchingEngine storeMatchingEngine) {
        this.aiClient = aiClient;
        this.toolRegistry = new ToolRegistry();
        this.storeMatchingEngine = storeMatchingEngine;
    }

    @PostConstruct
    public void initTools() {
        toolRegistry.register(new AiTool(
                "find_optimal_store",
                "Find the best nearby store based on user location and item availability.",
                Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                RADIUS_METERS, Map.of("type", "INTEGER", DESCRIPTION, "Search radius in meters (default 5000)."),
                                ITEM_IDS, Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), DESCRIPTION, "List of catalog product UUIDs to match in inventory.")
                        ),
                        "required", List.of(ITEM_IDS)
                ),
                (args, context) -> {
                    Double lat = (Double) context.get("latitude");
                    Double lng = (Double) context.get("longitude");
                    if (lat == null || lng == null) return "User location not provided. Cannot search stores.";
                    int radius = (args.get(RADIUS_METERS) != null) ? (Integer) args.get(RADIUS_METERS) : 5000;
                    Object rawItemIds = args.get(ITEM_IDS);
                    if (!(rawItemIds instanceof List<?> rawIdList)) return "Item IDs not provided or invalid.";
                    
                    @SuppressWarnings("unchecked")
                    List<String> idStrings = rawIdList.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .toList();
                            
                    List<UUID> itemIds = idStrings.stream().map(UUID::fromString).toList();
                    return storeMatchingEngine.findOptimalStores(lat, lng, radius, itemIds);
                }
        ));
    }

    public String extractFromMultimodal(MultipartFile image, String text, Double latitude, Double longitude, String userEmail) {
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
        context.put("userEmail", userEmail);

        return processAiResponseLoop(messages, context);
    }

    private String processAiResponseLoop(List<AiMessage> messages, Map<String, Object> context) {
        // 1. Facem un SINGUR apel rapid către AI (scutim quota și timp)
        AiMessage response = aiClient.generateResponse(messages, toolRegistry.getAvailableTools());
        messages.add(response);

        List<AiMessage.ToolCallPart> toolCalls = response.parts().stream()
                .filter(p -> p instanceof AiMessage.ToolCallPart)
                .map(p -> (AiMessage.ToolCallPart) p)
                .toList();

        // 2. Dacă a folosit tool-ul de locație (find_optimal_store), îl rulăm
        if (!toolCalls.isEmpty()) {
            List<AiMessage.Part> toolResponses = new ArrayList<>();
            for (AiMessage.ToolCallPart call : toolCalls) {
                Object result = toolRegistry.executeTool(call.name(), call.arguments(), context);
                toolResponses.add(new AiMessage.ToolResponsePart(call.name(), result));
            }
            messages.add(new AiMessage("function", toolResponses));

            // Lăsăm AI-ul să includă magazinul în textul final
            AiMessage locationResponse = aiClient.generateResponse(messages, Collections.emptyList());
            String rawResponse = locationResponse.parts().stream()
                    .filter(p -> p instanceof AiMessage.TextPart)
                    .map(p -> ((AiMessage.TextPart) p).text())
                    .collect(Collectors.joining("\n"));
            return finalizeStructuredJson(messages, rawResponse);
        }

        // 3. Dacă nu e nevoie de locație, returnăm instant textul generat!
        String rawResponse = response.parts().stream()
                .filter(p -> p instanceof AiMessage.TextPart)
                .map(p -> ((AiMessage.TextPart) p).text())
                .collect(Collectors.joining("\n"));

        return finalizeStructuredJson(messages, rawResponse);
    }

    public String extractIngredientsAsJson(String rawRecipeText) {
        // Punem null ca userEmail aici pentru cazurile simple fara autentificare
        return extractFromMultimodal(null, rawRecipeText, null, null, null);
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
            // Treat unreadable bytes as an unsupported image format.
        }
        return null;
    }

    private String finalizeStructuredJson(List<AiMessage> messages, String rawResponse) {
        List<AiMessage> finalMessages = new ArrayList<>(messages);
        finalMessages.add(new AiMessage("user", List.of(
                new AiMessage.TextPart(FINAL_JSON_PROMPT + "\n\nPrevious draft:\n" + rawResponse)
        )));

        AiMessage finalizedResponse = aiClient.generateResponse(finalMessages, Collections.emptyList());
        String finalizedText = finalizedResponse.parts().stream()
                .filter(p -> p instanceof AiMessage.TextPart)
                .map(p -> ((AiMessage.TextPart) p).text())
                .collect(Collectors.joining("\n"))
                .trim();

        if (!finalizedText.isEmpty()) {
            return finalizedText;
        }

        return rawResponse;
    }
}