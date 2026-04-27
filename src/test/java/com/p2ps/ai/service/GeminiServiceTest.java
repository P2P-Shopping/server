package com.p2ps.ai.service;

import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.exception.AiProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private CatalogService catalogService;

    private GeminiService geminiService;
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() throws Exception {
        geminiService = new GeminiService(catalogService);
        restTemplate = mock(RestTemplate.class);

        Field restField = GeminiService.class.getDeclaredField("restTemplate");
        restField.setAccessible(true);
        restField.set(geminiService, restTemplate);

        Field apiUrlField = GeminiService.class.getDeclaredField("apiUrl");
        apiUrlField.setAccessible(true);
        apiUrlField.set(geminiService, "http://fake-api-url");

        Field apiKeyField = GeminiService.class.getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        apiKeyField.set(geminiService, "fake-api-key");
    }

    @Test
    void whenRestThrows_shouldWrapInAiProcessingException() {
        when(catalogService.getTopPopularProducts()).thenReturn(List.of());
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        assertThatThrownBy(() -> geminiService.extractIngredientsAsJson("text"))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("Error during Multimodal AI processing");
    }

    @Test
    void whenNoCandidates_shouldThrow() {
        when(catalogService.getTopPopularProducts()).thenReturn(List.of());
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"candidates\":[]}"));

        assertThatThrownBy(() -> geminiService.extractIngredientsAsJson("text"))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("Google API returned an empty response.");
    }

    @Test
    void success_withTextOnly_returnsParsedJson() {
        ProductCatalog mockProduct = new ProductCatalog();
        mockProduct.setId(UUID.randomUUID());
        mockProduct.setGenericName("Lapte");
        when(catalogService.getTopPopularProducts()).thenReturn(List.of(mockProduct));

        String innerJson = "{\"listType\":\"RECIPE\",\"items\":[{\"genericName\":\"Lapte\",\"catalogId\":\"" + mockProduct.getId() + "\"}]}";
        String googleResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + innerJson.replace("\"", "\\\"") + "\"}]}}]}";

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(googleResponse));

        // Act
        String result = geminiService.extractIngredientsAsJson("Vreau o rețetă cu lapte.");

        // Assert
        assertThat(result).isEqualTo(innerJson);
    }

    @Test
    void success_withImageAndTextMultimodal_returnsParsedJson() {
        // Arrange
        when(catalogService.getTopPopularProducts()).thenReturn(List.of());

        String innerJson = "{\"listType\":\"NORMAL\",\"items\":[]}";
        String googleResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + innerJson.replace("\"", "\\\"") + "\"}]}}]}";

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(googleResponse));

        MockMultipartFile mockImage = new MockMultipartFile("image", "fridge.png", "image/png", "fake-pixel-data".getBytes());

        // Act
        String result = geminiService.extractFromMultimodal(mockImage, "Ce am în frigider?");

        // Assert
        assertThat(result).isEqualTo(innerJson);
    }
}