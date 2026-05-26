package com.p2ps.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.service.ProductResolutionService;
import com.p2ps.exception.AiProcessingException;
import com.p2ps.util.ProductStringUtils;
import com.p2ps.util.QuantityParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AiOrchestrationService {

    private final AiService aiService;
    private final AiPersistenceService aiPersistenceService;
    private final ProductResolutionService productResolutionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AiOrchestrationService(
            AiService aiService,
            AiPersistenceService aiPersistenceService,
            ProductResolutionService productResolutionService,
            UserRepository userRepository,
            Optional<ObjectMapper> objectMapper
    ) {
        this.aiService = aiService;
        this.aiPersistenceService = aiPersistenceService;
        this.productResolutionService = productResolutionService;
        this.userRepository = userRepository;
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
    public AiGenerationResponse generateShoppingItems(MultipartFile image, String text, Double latitude, Double longitude, String userEmail) {
        int maxRetries = 2;
        Exception lastException = null;
        String lastRawResult = null;
        AiGenerationResponse response = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                // Receive the generated JSON from AI Service
                String rawResult = aiService.extractFromMultimodal(image, text, latitude, longitude, userEmail);
                lastRawResult = rawResult;
                String jsonResult = extractJson(rawResult);

                // Map the JSON to the response object
                response = objectMapper.readValue(jsonResult, AiGenerationResponse.class);

                // Validation
                if (response != null && response.getItems() != null && !response.getItems().isEmpty()) {
                    break; // Success
                }
            } catch (AiProcessingException e) {
                if (e.getStatus() != HttpStatus.UNPROCESSABLE_CONTENT) {
                    throw e;
                }
                lastException = e;
            } catch (Exception e) {
                lastException = e;
            }
            response = null; // Reset if failed validation
        }

        if (response != null) {
            Users user = null;
            if (userEmail != null && !userEmail.isBlank()) {
                user = userRepository.findByEmail(userEmail).orElse(null);
            }
            normalizeDetectedProducts(response, user);
            return response;
        }

        throw new AiProcessingException(
                "AI returned an invalid structure after retries. Expected AiGenerationResponse. Raw AI snippet: " +
                        abbreviate(lastRawResult),
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

    private String abbreviate(String raw) {
        if (raw == null) return "<null>";
        String sanitized = raw.replaceAll("\\s+", " ").trim();
        if (sanitized.length() <= 240) return sanitized;
        return sanitized.substring(0, 240) + "...";
    }

    private void normalizeDetectedProducts(AiGenerationResponse response, Users user) {
        // Extragem email-ul în siguranță pentru a preveni NullPointerException
        String email = (user != null) ? user.getEmail() : null;
        String listType = response.getListType();
        boolean recipeList = listType != null && "RECIPE".equalsIgnoreCase(listType.trim());

        for (ParsedItemResponse item : response.getItems()) {
            if (item != null) {
                String keyword = ProductStringUtils.firstNonBlank(item.getGenericName(), item.getSpecificName(), item.getBrand());
                if (keyword != null) {
                    // Java face căutarea INSTANT, direct pe backend!
                    productResolutionService.resolveForUser(keyword, email)
                            .ifPresent(match -> applyResolvedProduct(item, match, recipeList));
                }
            }
        }
    }

    private void applyResolvedProduct(ParsedItemResponse item, ProductResolutionService.ResolvedProduct match, boolean recipeList) {
        ProductCatalog catalogProduct = match.catalogProduct();
        item.setGenericName(ProductStringUtils.firstNonBlank(
                match.matchedName(),
                catalogProduct != null ? catalogProduct.getGenericName() : null,
                item.getGenericName()
        ));

        if (catalogProduct != null) {
            item.setCatalogId(catalogProduct.getId() != null ? catalogProduct.getId().toString() : item.getCatalogId());
            item.setSpecificName(ProductStringUtils.firstNonBlank(catalogProduct.getSpecificName(), item.getSpecificName()));
            item.setBrand(ProductStringUtils.firstNonBlank(catalogProduct.getBrand(), item.getBrand(), match.brand()));
            item.setCategory(ProductStringUtils.firstNonBlank(catalogProduct.getCategory(), item.getCategory(), match.category()));
            applyRecipeQuantityNormalization(item, catalogProduct, recipeList);
            return;
        }

        item.setBrand(ProductStringUtils.firstNonBlank(item.getBrand(), match.brand()));
        item.setCategory(ProductStringUtils.firstNonBlank(item.getCategory(), match.category()));
    }

    private void applyRecipeQuantityNormalization(ParsedItemResponse item, ProductCatalog catalogProduct, boolean recipeList) {
        if (!recipeList || catalogProduct == null) {
            return;
        }

        String defaultQuantity = catalogProduct.getDefaultQuantity();
        String itemQuantity = item.getQuantity();
        String itemUnit = item.getUnit();
        if (defaultQuantity == null || defaultQuantity.isBlank() || itemQuantity == null || itemQuantity.isBlank()
                || itemUnit == null || itemUnit.isBlank()) {
            return;
        }

        try {
            String converted = QuantityParser.convertToUnit(itemQuantity.trim() + " " + itemUnit.trim(), defaultQuantity.trim());
            QuantityParser.ParsedQuantity parsedConverted = QuantityParser.parse(converted);
            item.setQuantity(formatQuantityValue(parsedConverted.value()));
            item.setUnit(parsedConverted.unit().symbol());
        } catch (RuntimeException _) {
            // Keep the AI quantity when parsing or conversion fails.
        }
    }

    private String formatQuantityValue(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

}
