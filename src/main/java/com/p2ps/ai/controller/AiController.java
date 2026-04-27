package com.p2ps.ai.controller;

import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.ai.service.AiOrchestrationService;
import com.p2ps.util.ImageValidationUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiOrchestrationService aiOrchestrationService;
    private final ImageValidationUtil imageValidator;

    public AiController(AiOrchestrationService aiOrchestrationService, ImageValidationUtil imageValidator) {
        this.aiOrchestrationService = aiOrchestrationService;
        this.imageValidator=imageValidator;
    }

    @Deprecated
    @PostMapping("/recipe-to-list")
    public ResponseEntity<?> parseRecipe(@Valid @RequestBody RecipeRequest request) {
        return ResponseEntity.status(HttpStatus.GONE)
                .header("Deprecation", "true")
                .body(Map.of(
                        "error", "This endpoint is permanently removed due to the new Gatekeeper architecture.",
                        "migration", "Please use the multimodal /api/ai/generate endpoint."
                ));
    }

    // New multimodal endpoint
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateListMultimodal(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "text", required = false) String text,
            Principal principal) {

        // Has to send at least text or image
        if ((image == null || image.isEmpty()) && (text == null || text.trim().isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You have to send a text or an image."));
        }

        // If image was sent, we validate using external util
        if (image != null && !image.isEmpty()) {
            String format = imageValidator.detectImageFormat(image);
            if (format == null || (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("jpg") && !format.equalsIgnoreCase("png"))) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body(Map.of("error", "Invalid file. Only JPEG and PNG images allowed."));
            }
        }

        // Send data towards Orchestrator for AI processing
        // (Gatekeeper returns a response directly, not saving anything in database)
        AiGenerationResponse response = aiOrchestrationService.generateShoppingItems(image, text);

        return ResponseEntity.ok(response);
    }
}