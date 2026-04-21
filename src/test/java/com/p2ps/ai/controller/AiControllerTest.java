package com.p2ps.ai.controller;

import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.ai.service.AiOrchestrationService;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiControllerTest {

    private final AiOrchestrationService orchestration = mock(AiOrchestrationService.class);
    private final AiController controller = new AiController(orchestration);

    @Test
    void parseRecipe_callsServiceAndReturnsOk() {
        RecipeRequest req = new RecipeRequest();
        req.setText("recipe text");
        ParsedItemResponse p = new ParsedItemResponse();
        p.setName("Tomato");
        when(orchestration.processRecipeAndPopulateList(req, "user@e")).thenReturn(List.of(p));
        Principal principal = () -> "user@e";
        var resp = controller.parseRecipe(req, principal);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getName()).isEqualTo("Tomato");
    }
}
