package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.exception.AiProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiOrchestrationServiceTest {

    @Mock
    private AiService aiService;

    @Mock
    private AiPersistenceService aiPersistenceService;

    private AiOrchestrationService svc;

    @BeforeEach
    void setUp() {
        svc = new AiOrchestrationService(aiService, aiPersistenceService, Optional.of(new ObjectMapper()));
    }

    @ParameterizedTest
    @CsvSource({
            "invalid-json, AI could not return a correctly structured list",
            "'[]', AI did not return any valid ingredients",
            "null, AI could not return a correctly structured list"
    })
    void processRecipe_fails_throwsAiProcessingException(String aiOutput, String expectedErrorMessage) {
        when(aiService.extractIngredientsAsJson(anyString())).thenReturn(aiOutput);

        RecipeRequest req = new RecipeRequest();
        req.setText("text");

        assertThatThrownBy(() -> svc.processRecipeAndPopulateList(req, "u@e"))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining(expectedErrorMessage);
    }

    @Test
    void processRecipe_success_filtersInvalidItemsBeforePersisting() {
        String json = "[null,{\"genericName\":\"   \",\"quantity\":1,\"unit\":\"pieces\"},{\"genericName\":\"Tomato\",\"quantity\":2,\"unit\":\"pieces\"}]";
        when(aiService.extractIngredientsAsJson(anyString())).thenReturn(json);

        RecipeRequest req = new RecipeRequest();
        req.setText("text");
        req.setNewListTitle("New List");
        UUID listId = UUID.randomUUID();
        req.setListId(listId);

        var result = svc.processRecipeAndPopulateList(req, "u@e");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGenericName()).isEqualTo("Tomato");

        verify(aiPersistenceService).createListAndPopulateItems(listId, "New List", List.of(result.get(0)), "u@e");
    }

    @Test
    void processRecipe_success_delegatesToPersistenceService() {
        String json = "[{\"genericName\":\"Tomato\",\"quantity\":2,\"unit\":\"pieces\"}]";
        when(aiService.extractIngredientsAsJson(anyString())).thenReturn(json);

        RecipeRequest req = new RecipeRequest();
        req.setText("text");
        req.setNewListTitle("New List");
        UUID listId = UUID.randomUUID();
        req.setListId(listId);

        var result = svc.processRecipeAndPopulateList(req, "u@e");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGenericName()).isEqualTo("Tomato");

        verify(aiPersistenceService).createListAndPopulateItems(eq(listId), eq("New List"), anyList(), eq("u@e"));
    }


    @Test
    void generateShoppingItems_success_returnsParsedResponse() {
        MultipartFile mockImage = new MockMultipartFile("image", "test.jpg", "image/jpeg", "data".getBytes());
        String text = "Reteta clatite";
        String validJson = """
                {
                  "listType": "RECIPE",
                  "items": [
                    {
                      "genericName": "Lapte",
                      "category": "Lactate"
                    }
                  ]
                }
                """;

        when(aiService.extractFromMultimodal(mockImage, text, null, null)).thenReturn(validJson);

        AiGenerationResponse response = svc.generateShoppingItems(mockImage, text, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getListType()).isEqualTo("RECIPE");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getGenericName()).isEqualTo("Lapte");

        verifyNoInteractions(aiPersistenceService);
    }

    @Test
    void generateShoppingItems_invalidJson_throwsAiProcessingException() {
        when(aiService.extractFromMultimodal(null, "text", null, null)).thenReturn("I am an AI, I cannot give you JSON.");

        assertThatThrownBy(() -> svc.generateShoppingItems(null, "text", null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("AI returned an invalid structure")
                .hasMessageContaining("Raw AI snippet");
    }

    @Test
    void generateShoppingItems_emptyItems_throwsAiProcessingException() {
        String jsonWithEmptyItems = """
                {
                  "listType": "RECIPE",
                  "items": []
                }
                """;
        when(aiService.extractFromMultimodal(null, "text", null, null)).thenReturn(jsonWithEmptyItems);

        assertThatThrownBy(() -> svc.generateShoppingItems(null, "text", null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("AI returned an invalid structure");
    }

    @Test
    void generateShoppingItems_nullItems_throwsAiProcessingException() {
        String jsonWithNullItems = """
                {
                  "listType": "RECIPE"
                }
                """;
        when(aiService.extractFromMultimodal(null, "text", null, null)).thenReturn(jsonWithNullItems);

        assertThatThrownBy(() -> svc.generateShoppingItems(null, "text", null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("AI returned an invalid structure");
    }

    @Test
    void generateShoppingItems_withLatLong_passesToAiService() {
        String validJson = """
                {"listType":"NORMAL","items":[{"genericName":"Milk"}]}
                """;
        when(aiService.extractFromMultimodal(any(), any(), eq(45.0), eq(25.0))).thenReturn(validJson);

        AiGenerationResponse response = svc.generateShoppingItems(null, "text", 45.0, 25.0);

        assertThat(response.getItems()).hasSize(1);
        verify(aiService).extractFromMultimodal(null, "text", 45.0, 25.0);
    }

    @ParameterizedTest(name = "extractJson: input={0}, expected={1}")
    @MethodSource("extractJsonTestCases")
    void extractJson_returnsExpected(String input, String expected) throws Exception {
        java.lang.reflect.Method method = AiOrchestrationService.class.getDeclaredMethod("extractJson", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(svc, input);
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> extractJsonTestCases() {
        return Stream.of(
                Arguments.of("Here is the data: {\"key\":\"value\"} done.", "{\"key\":\"value\"}"),
                Arguments.of("Data: [1,2,3] end.", "[1,2,3]"),
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of("   ", "   "),
                Arguments.of("No JSON here", "No JSON here")
        );
    }

    @Test
    void processRecipeAndPopulateList_retriesOnFailure() {
        when(aiService.extractIngredientsAsJson(anyString()))
                .thenThrow(new RuntimeException("Try again"))
                .thenReturn("[{\"genericName\":\"Tomato\",\"quantity\":2}]");

        RecipeRequest req = new RecipeRequest();
        req.setText("text");
        req.setNewListTitle("New List");
        UUID listId = UUID.randomUUID();
        req.setListId(listId);

        var result = svc.processRecipeAndPopulateList(req, "u@e");

        assertThat(result).hasSize(1);
        verify(aiService, times(2)).extractIngredientsAsJson(anyString());
    }

    @Test
    void generateShoppingItems_retriesOnFailure() {
        when(aiService.extractFromMultimodal(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Fail"))
                .thenReturn("{\"listType\":\"RECIPE\",\"items\":[{\"genericName\":\"Milk\"}]}");

        AiGenerationResponse response = svc.generateShoppingItems(null, "text", null, null);

        assertThat(response.getItems()).hasSize(1);
        verify(aiService, times(2)).extractFromMultimodal(any(), any(), any(), any());
    }
}
