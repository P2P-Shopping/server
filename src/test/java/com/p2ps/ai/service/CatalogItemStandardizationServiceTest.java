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
                        {"cleanName":"Greek Yogurt","category":"Dairy","brand":"Olympus"}
                        """)))
        );

        // Am adăugat null pentru noile câmpuri
        CatalogStandardizationResult result = service.standardize("iaurt grecesc", null, null, null);

        assertThat(result.cleanName()).isEqualTo("Greek Yogurt");
        assertThat(result.category()).isEqualTo("Dairy");
        assertThat(result.brand()).isEqualTo("Olympus");
    }

    @Test
    void standardizeShouldTrimBrandAndAllowNullBrand() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());
        when(aiClient.generateResponse(any(), eq(List.of()))).thenReturn(
                new AiMessage("model", List.of(new AiMessage.TextPart("""
                        {"cleanName":"Tomatoes","category":"Produce","brand":"   "}
                        """)))
        );

        // Am adăugat null pentru noile câmpuri
        CatalogStandardizationResult result = service.standardize("rosii", null, null, null);

        assertThat(result.brand()).isNull();
    }

    @Test
    void standardizeShouldRejectBlankInput() {
        CatalogItemStandardizationService service = new CatalogItemStandardizationService(aiClient, new ObjectMapper());

        // Am adăugat null pentru noile câmpuri
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

        // Am adăugat null pentru noile câmpuri
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
                        {"cleanName":"Milk","category":"Dairy","brand":null}
                        """)))
        );

        // Am adăugat null pentru noile câmpuri
        service.standardize("milk", null, null, null);

        ArgumentCaptor<List<AiMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).generateResponse(captor.capture(), eq(List.of()));

        // Am schimbat hasSize(2) în hasSize(1) pentru că trimitem un singur mesaj combinat acum!
        assertThat(captor.getValue()).hasSize(1);
        assertThat(((AiMessage.TextPart) captor.getValue().get(0).parts().get(0)).text())
                .contains("Return ONLY one valid JSON object");
    }
}
