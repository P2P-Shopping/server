package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.exception.AiProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogItemStandardizationServiceTest {

    @Mock
    private AiClient aiClient;

    @Test
    void standardizeShouldParseStructuredResponse() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("""
                        {"cleanName":"Greek Yogurt","category":"Dairy","brand":"Olympus","defaultQuantity":"1 buc"}
                        """)))
        );

        CatalogStandardizationResult result = service.standardize("iaurt grecesc", null, null, null);

        assertThat(result.cleanName()).isEqualTo("Greek Yogurt");
        assertThat(result.category()).isEqualTo("Dairy");
        assertThat(result.brand()).isEqualTo("Olympus");
        assertThat(result.defaultQuantity()).isEqualTo("1 buc");
    }

    @Test
    void standardizeShouldTrimBrandAndAllowNullBrand() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("""
                        {"cleanName":"Tomatoes","category":"Produce","brand":"   ","defaultQuantity":"1 kg"}
                        """)))
        );

        CatalogStandardizationResult result = service.standardize("rosii", null, null, null);

        assertThat(result.brand()).isNull();
        assertThat(result.defaultQuantity()).isEqualTo("1 kg");
    }

    @Test
    void standardizeShouldRejectBlankInput() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());

        assertThatThrownBy(() -> service.standardize("  ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void standardizeShouldThrowWhenResponseIsNotJson() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("not-json")))
        );

        assertThatThrownBy(() -> service.standardize("lapte", null, null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("valid JSON object");
    }

    @Test
    void standardizeShouldThrowWhenAiReturnsNullPayload() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(null);

        assertThatThrownBy(() -> service.standardize("lapte", null, null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("empty catalog standardization response");
    }

    @Test
    void standardizeShouldSendStrictPromptToAiClient() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("""
                        {"cleanName":"Milk","category":"Dairy","brand":null,"defaultQuantity":"1 L"}
                        """)))
        );

        service.standardize("milk", null, null, null);

        ArgumentCaptor<List<AiMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).generateResponse(captor.capture(), eq(List.of()));

        assertThat(captor.getValue()).hasSize(1);
        assertThat(((AiMessage.TextPart) captor.getValue().get(0).parts().get(0)).text())
                .contains("Return ONLY one valid JSON object");
    }

    @Test
    void standardizeShouldAcceptJsonEmbeddedInSurroundingText() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("""
                        Here is the normalized product:
                        {"cleanName":"Ardei","category":"Fructe și Legume","brand":null,"defaultQuantity":"1 kg"}
                        """)))
        );

        CatalogStandardizationResult result = service.standardize("ardei", null, null, null);

        assertThat(result.cleanName()).isEqualTo("Ardei");
        assertThat(result.category()).isEqualTo("Fructe și Legume");
        assertThat(result.defaultQuantity()).isEqualTo("1 kg");
    }

    @Test
    void standardizeShouldThrowWhenAiReturnsBlankText() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("   ")))
        );

        assertThatThrownBy(() -> service.standardize("lapte", null, null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("empty catalog standardization response");
    }

    @Test
    void standardizeShouldThrowWhenRequiredFieldIsMissing() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("""
                        {"cleanName":"Lapte","category":" ","brand":"Zuzu","defaultQuantity":"1 L"}
                        """)))
        );

        assertThatThrownBy(() -> service.standardize("lapte", null, null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("missing field: category");
    }

    @Test
    void standardizeShouldIncludeUserHintsInPrompt() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("""
                        {"cleanName":"Lapte Zuzu 1L","category":"Lactate și Ouă","brand":"Zuzu","defaultQuantity":"1 L"}
                        """)))
        );

        service.standardize("lapte", "Zuzu", "Lactate și Ouă", new java.math.BigDecimal("8.99"));

        ArgumentCaptor<List<AiMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).generateResponse(captor.capture(), eq(List.of()));
        String prompt = ((AiMessage.TextPart) captor.getValue().get(0).parts().get(0)).text();
        assertThat(prompt).contains("Hint - User provided brand: Zuzu");
        assertThat(prompt).contains("Hint - User provided category: Lactate și Ouă");
        assertThat(prompt).contains("Hint - User provided price: 8.99");
    }

    @Test
    void standardizeShouldThrowWhenDefaultQuantityIsTooLong() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        String longQuantity = "a".repeat(51);
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart(String.format("""
                        {"cleanName":"Product","category":"Produce","brand":null,"defaultQuantity":"%s"}       
                        """, longQuantity))))
        );

        assertThatThrownBy(() -> service.standardize("product", null, null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("too long (max 50 chars)");
    }

    @Test
    void standardizeShouldThrowWhenDefaultQuantityIsMissing() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("""
                        {"cleanName":"Product","category":"Produce","brand":null,"defaultQuantity":"  "}
                        """)))
        );

        assertThatThrownBy(() -> service.standardize("product", null, null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("missing field: defaultQuantity");
    }
    }
