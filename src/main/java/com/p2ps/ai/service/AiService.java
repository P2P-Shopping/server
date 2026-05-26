package com.p2ps.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.ai.core.AiTool;
import com.p2ps.ai.core.ToolRegistry;
import com.p2ps.exception.AiProcessingException;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.service.StoreMatchingEngine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final AiClient aiClient;
    private final ToolRegistry toolRegistry;
    private final StoreMatchingEngine storeMatchingEngine;

    private static final String SYSTEM_PROMPT ="""
        You are a strict multimodal culinary data parser. Your ONLY job is to analyze text and/or images to output a structured grocery list.
        LANGUAGE RULE (CRITICAL): If the user writes in Romanian, respond in Romanian.
        MULTI-ITEM RULE (CRITICAL): If the user mentions multiple grocery items in one prompt, you MUST return one separate object inside items for EACH item.
        Never merge multiple requested products into one item. For example, 'sugar and bananas' must produce two items: one for sugar and one for bananas.
        VISUAL RULES (CRITICAL):
        1. If the user uploads a photo of a FINISHED DISH, deduce the recipe and output raw ingredients.
        2. If the user uploads a photo of a FRIDGE/PANTRY, identify items and deduce missing ingredients if asked.
        3. If the user uploads a PHOTO of a RECEIPT, extract all products, brands, and quantities.
        EXTRACTION RULE (CRITICAL): Extract EXACTLY what the user said. DO NOT invent or hallucinate brands, specific names, or catalog IDs. Our backend will handle mapping to the database. Leave catalogId null. Leave brand null unless the user or receipt explicitly specifies a brand.
        RULE 1 (RECEIPT PRICE): For receipt photos, also extract the product price when visible and include it in the output.
        RULE 2 (LOCATION AWARENESS): If user coordinates are provided, use the 'find_optimal_store' tool to recommend the best place to shop.
        RULE 3 (TIERED CATEGORIZATION): Classify the list as 'RECIPE', 'FREQUENT', or 'CART'.
        RECIPE LOGIC: If the user describes a dish, dessert, meal, or recipe idea (e.g., negresa, clatite, ciorba, pasta, cake), classify it as 'RECIPE' even if the word 'recipe' is not used.
        RECIPE QUANTITY RULE (CRITICAL): For RECIPE outputs, estimate a realistic required quantity for each ingredient and always return both quantity and unit when the ingredient is measurable. Return the amount needed by the recipe itself, not package size or store packaging. If the exact amount is unclear, return your best conservative estimate.
        CRITICAL CATEGORY RULE: The 'category' field MUST be chosen EXACTLY from this strict list: [Fructe și Legume, Lactate și Ouă, Carne, Băcănie, Dulciuri, Curățenie, Altele]. DO NOT invent categories!
        Format: {"listType": "string", "suggestedStore": "string or null", "items": [{"genericName": "string", "specificName": "string or null", "brand": "string or null", "quantity": number or null, "unit": "string or null", "catalogId": "string or null", "category": "string", "price": number or null}]}.
        """;
    private static final String FINAL_JSON_PROMPT =
            "Return ONLY valid JSON matching exactly this schema and nothing else: " +
                    "{\"listType\":\"RECIPE|FREQUENT|CART\",\"suggestedStore\":\"string or null\",\"items\":[{\"genericName\":\"string\",\"specificName\":\"string or null\",\"brand\":\"string or null\",\"quantity\":number or null,\"unit\":\"string or null\",\"catalogId\":\"string or null\",\"category\":\"string\",\"price\":number or null}]}. " +
                    "For multi-item requests, items MUST contain all requested items as separate objects. " +
                    " Remember to use the STRICT category list. Do not add markdown, explanations, or prose.";
    
    private static final String POST_VALIDATION_SYSTEM_PROMPT = """
            You are a strict data standardizer and receipt filter. You will receive a JSON array of grocery items. Your ONLY job is to filter out junk and standardize the remaining items.
            1. CRITICAL EXCLUSION RULE: Identify and REMOVE non-consumable/non-product items. Completely drop items such as: Warranties (Garanție, Extragaranție), Bottle deposits/Eco taxes (SGR, RetuRO, Taxă ambalaj), Shopping bags (Pungă, Sacoșă), Discounts/Vouchers, and Delivery fees.
            2. Standardize units of measurement (e.g., convert '1000g' to '1kg').
            3. Fix casing and formatting (e.g., Title Case for product names).
            4. Clean up weird POS abbreviations without changing the core product meaning.
            5. STRICT MAPPING RULE: For the items you keep, you MUST NEVER change the 'id', 'price', or 'catalogId'. Only refine the 'name', 'brand', and 'quantity' strings.
            6. Return ONLY the filtered and refined list of objects as a strict JSON array. No markdown, no explanations.
            """;
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

    public List<ItemDTO> postValidateAndFilterReceiptItems(List<ItemDTO> mappedItems) {
        if (mappedItems == null || mappedItems.isEmpty()) {
            return mappedItems;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();

            // 💡 REZOLVAREA BOT-ULUI: Construim o proiecție doar cu câmpurile strict necesare
            List<Map<String, Object>> trimmedItems = mappedItems.stream().map(item -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", item.getId()); // Păstrăm ID-ul pentru tracking în ItemService
                map.put("name", item.getName());
                map.put("brand", item.getBrand());
                map.put("quantity", item.getQuantity());
                map.put("price", item.getPrice());
                map.put("category", item.getCategory());
                return map;
            }).toList();

            // AI-ul primește acum un JSON mic, curat și fără metadata sensibile!
            String jsonInput = mapper.writeValueAsString(trimmedItems);

            List<AiMessage> messages = new ArrayList<>();
            messages.add(new AiMessage("system", List.of(new AiMessage.TextPart(POST_VALIDATION_SYSTEM_PROMPT))));
            messages.add(new AiMessage("user", List.of(new AiMessage.TextPart(jsonInput))));

            AiMessage response = aiClient.generateResponse(messages, Collections.emptyList());

            // ... restul codului rămâne absolut identic (extragerea și maparea înapoi la ItemDTO)
            // AI-ul va returna JSON-ul, iar Jackson va mapa înapoi DOAR câmpurile pe care i le-am trimis

            String rawResponse = response.parts().stream()
                    .filter(p -> p instanceof AiMessage.TextPart)
                    .map(p -> ((AiMessage.TextPart) p).text())
                    .collect(Collectors.joining("\n"));

            String jsonResult = extractJsonArray(rawResponse);
            List<ItemDTO> filteredItems = mapper.readValue(jsonResult, new TypeReference<List<ItemDTO>>() {});

            if (filteredItems != null) {
                return filteredItems;
            }
        } catch (Exception e) {
            logger.error("Failed to execute AI post-validation and filtering. Falling back to original items.", e);
        }

        return mappedItems;
    }

    private String extractJsonArray(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start != -1 && end != -1 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
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
        AiMessage response = aiClient.generateResponse(messages, toolRegistry.getAvailableTools());
        messages.add(response);

        List<AiMessage.ToolCallPart> toolCalls = response.parts().stream()
                .filter(p -> p instanceof AiMessage.ToolCallPart)
                .map(p -> (AiMessage.ToolCallPart) p)
                .toList();

        if (!toolCalls.isEmpty()) {
            List<AiMessage.Part> toolResponses = new ArrayList<>();
            for (AiMessage.ToolCallPart call : toolCalls) {
                Object result = toolRegistry.executeTool(call.name(), call.arguments(), context);
                toolResponses.add(new AiMessage.ToolResponsePart(call.name(), result));
            }
            messages.add(new AiMessage("function", toolResponses));

            AiMessage locationResponse = aiClient.generateResponse(messages, Collections.emptyList());
            String rawResponse = locationResponse.parts().stream()
                    .filter(p -> p instanceof AiMessage.TextPart)
                    .map(p -> ((AiMessage.TextPart) p).text())
                    .collect(Collectors.joining("\n"));
            return finalizeStructuredJson(messages, rawResponse);
        }

        String rawResponse = response.parts().stream()
                .filter(p -> p instanceof AiMessage.TextPart)
                .map(p -> ((AiMessage.TextPart) p).text())
                .collect(Collectors.joining("\n"));

        return finalizeStructuredJson(messages, rawResponse);
    }

    public String extractIngredientsAsJson(String rawRecipeText) {
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
            // Ignore
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
