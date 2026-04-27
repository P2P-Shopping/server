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

    // Semnătură reală și suficient de lungă de PNG (1x1 pixel transparent)
    // pentru a trece de validarea strictă cu ImageIO
    private static final byte[] VALID_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0B, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0x99, 0x63, 0x60, 0x00, 0x02, 0x00,
            0x00, 0x05, 0x00, 0x01, 0x22, 0x26, 0x05, (byte) 0xC3,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

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
    void whenNoParts_shouldThrow() {
        when(catalogService.getTopPopularProducts()).thenReturn(List.of());
        // JSON care nu are array-ul de "parts" înăuntru
        String googleResponse = "{\"candidates\":[{\"content\":{}}]}";

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(googleResponse));

        assertThatThrownBy(() -> geminiService.extractIngredientsAsJson("text"))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("Google API returned candidate without text parts.");
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

        String result = geminiService.extractIngredientsAsJson("Vreau o rețetă cu lapte.");

        assertThat(result).isEqualTo(innerJson);
    }

    @Test
    void success_withImageAndTextMultimodal_returnsParsedJson() {
        when(catalogService.getTopPopularProducts()).thenReturn(List.of());

        String innerJson = "{\"listType\":\"NORMAL\",\"items\":[]}";
        String googleResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + innerJson.replace("\"", "\\\"") + "\"}]}}]}";

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(googleResponse));

        // Folosim VALID_PNG pentru a trece de noul detector cu ImageIO
        MockMultipartFile mockImage = new MockMultipartFile("image", "fridge.png", "image/png", VALID_PNG);

        String result = geminiService.extractFromMultimodal(mockImage, "Ce am în frigider?");

        assertThat(result).isEqualTo(innerJson);
    }

    @Test
    void extractFromMultimodal_withInvalidImageSpoofing_throwsException() {
        // Trimitem date false (care vor pica la parsarea ImageIO)
        MockMultipartFile fakeImage = new MockMultipartFile("image", "virus.png", "image/png", "fake-pixel-data".getBytes());

        assertThatThrownBy(() -> geminiService.extractFromMultimodal(fakeImage, "Ce am în poză?"))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("Unsupported or corrupted image format");
    }
}