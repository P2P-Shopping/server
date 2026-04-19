package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.exception.AiProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiOrchestrationServiceTest {

    GeminiService geminiService = mock(GeminiService.class);
    AiPersistenceService aiPersistenceService = mock(AiPersistenceService.class);

    @Test
    void invalidJson_throwsAiProcessingException() {
        AiOrchestrationService svc = new AiOrchestrationService(geminiService, aiPersistenceService, Optional.of(new ObjectMapper()));
        when(geminiService.extractIngredientsAsJson(anyString())).thenReturn("invalid-json");

        RecipeRequest req = new RecipeRequest();
        req.setText("text");

        assertThatThrownBy(() -> svc.processRecipeAndPopulateList(req, "u@e"))
                .isInstanceOf(AiProcessingException.class);
    }

    @Test
    void emptyItems_throwsAiProcessingException() {
        AiOrchestrationService svc = new AiOrchestrationService(geminiService, aiPersistenceService, Optional.of(new ObjectMapper()));
        when(geminiService.extractIngredientsAsJson(anyString())).thenReturn("[]");

        RecipeRequest req = new RecipeRequest();
        req.setText("text");

        assertThatThrownBy(() -> svc.processRecipeAndPopulateList(req, "u@e"))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("AI did not return any valid ingredients");
    }

    @Test
    void nullPayload_throwsAiProcessingException() {
        AiOrchestrationService svc = new AiOrchestrationService(geminiService, aiPersistenceService, Optional.of(new ObjectMapper()));
        when(geminiService.extractIngredientsAsJson(anyString())).thenReturn("null");

        RecipeRequest req = new RecipeRequest();
        req.setText("text");

        assertThatThrownBy(() -> svc.processRecipeAndPopulateList(req, "u@e"))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("null payload");
    }

    @Test
    void success_filtersInvalidItemsBeforePersisting() {
        AiOrchestrationService svc = new AiOrchestrationService(geminiService, aiPersistenceService, Optional.of(new ObjectMapper()));
        String json = "[null,{\"name\":\"   \",\"quantity\":1,\"unit\":\"pieces\"},{\"name\":\"Tomato\",\"quantity\":2,\"unit\":\"pieces\"}]";
        when(geminiService.extractIngredientsAsJson(anyString())).thenReturn(json);

        RecipeRequest req = new RecipeRequest();
        req.setText("text");
        req.setNewListTitle("New List");
        UUID listId = UUID.randomUUID();
        req.setListId(listId);

        var result = svc.processRecipeAndPopulateList(req, "u@e");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Tomato");

        verify(aiPersistenceService).createListAndPopulateItems(eq(listId), eq("New List"), eq(List.of(result.get(0))), eq("u@e"));
    }

    @Test
    void success_delegatesToPersistenceService() {
        AiOrchestrationService svc = new AiOrchestrationService(geminiService, aiPersistenceService, Optional.of(new ObjectMapper()));
        String json = "[{\"name\":\"Tomato\",\"quantity\":2,\"unit\":\"pieces\"}]";
        when(geminiService.extractIngredientsAsJson(anyString())).thenReturn(json);

        RecipeRequest req = new RecipeRequest();
        req.setText("text");
        req.setNewListTitle("New List");
        UUID listId = UUID.randomUUID();
        req.setListId(listId);

        var result = svc.processRecipeAndPopulateList(req, "u@e");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Tomato");

        verify(aiPersistenceService).createListAndPopulateItems(eq(listId), eq("New List"), anyList(), eq("u@e"));
    }
}