package com.p2ps.ai.controller;

import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.service.AiOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiOrchestrationService aiOrchestrationService;

    public AiController(AiOrchestrationService aiOrchestrationService) {
        this.aiOrchestrationService = aiOrchestrationService;
    }

    @PostMapping("/recipe-to-list")
    public ResponseEntity<List<ParsedItemResponse>> parseRecipe(@Valid @RequestBody RecipeRequest request, Principal principal) {
        String userEmail = principal.getName();
        List<ParsedItemResponse> response = aiOrchestrationService.processRecipeAndPopulateList(request, userEmail);
        return ResponseEntity.ok(response);
    }
}