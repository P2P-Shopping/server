package com.p2ps.ai.service;

import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.exception.AiProcessingException;
import com.p2ps.service.StoreMatchingEngine;
import com.p2ps.service.StoreMatchingEngine.StoreMatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private CatalogService catalogService; // Added mock for CatalogService

    @Mock
    private StoreMatchingEngine storeMatchingEngine;

    private AiService aiService;

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

    private static final String TEST_USER_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() throws Exception {
        // Updated constructor to include catalogService
        aiService = new AiService(aiClient, storeMatchingEngine);

        Field toolRegistryField = AiService.class.getDeclaredField("toolRegistry");
        toolRegistryField.setAccessible(true);
        toolRegistryField.set(aiService, new com.p2ps.ai.core.ToolRegistry());
        aiService.initTools();
    }

    @Test
    void extractIngredientsAsJson_returnsModelText() {
        when(aiClient.generateResponse(any(), any())).thenReturn(new AiMessage("model", List.of(new AiMessage.TextPart("{\"listType\":\"RECIPE\",\"items\":[]}"))));

        String result = aiService.extractIngredientsAsJson("text");

        assertThat(result).contains("\"listType\":\"RECIPE\"");
    }

    @Test
    void extractIngredientsAsJson_withToolCall_loopExecutesTool() {
        UUID itemId = UUID.randomUUID();
        AiMessage toolCallResponse = new AiMessage("model", List.of(
                new AiMessage.ToolCallPart("find_optimal_store", Map.of("item_ids", List.of(itemId.toString())))
        ));
        AiMessage locationResponse = new AiMessage("model", List.of(
                new AiMessage.TextPart("Suggested store: Mega")
        ));
        AiMessage finalizedResponse = new AiMessage("model", List.of(
                new AiMessage.TextPart("{\"listType\":\"RECIPE\",\"suggestedStore\":\"Mega\",\"items\":[{\"genericName\":\"Milk\"}]}")
        ));
        when(aiClient.generateResponse(any(), any()))
                .thenReturn(toolCallResponse)
                .thenReturn(locationResponse)
                .thenReturn(finalizedResponse);
        // Updated mock to findOptimalStores which returns a List
        when(storeMatchingEngine.findOptimalStores(45.0, 25.0, 5000, List.of(itemId)))
                .thenReturn(List.of(new StoreMatchResult("store-1", "Mega", 1, 120.0)));

        String result = aiService.extractFromMultimodal(null, "milk recipe", 45.0, 25.0, TEST_USER_EMAIL);

        assertThat(result)
                .contains("\"genericName\":\"Milk\"")
                .contains("\"suggestedStore\":\"Mega\"");
        verify(storeMatchingEngine).findOptimalStores(45.0, 25.0, 5000, List.of(itemId));
    }

    @Test
    void extractFromMultimodal_withInvalidImage_throwsException() {
        MultipartFile fakeImage = new MockMultipartFile("image", "virus.png", "image/png", "fake-pixel-data".getBytes());

        assertThatThrownBy(() -> aiService.extractFromMultimodal(fakeImage, "text", null, null, TEST_USER_EMAIL))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("Unsupported or corrupted image format");
    }

    @Test
    void extractFromMultimodal_withImageAndText_returnsTextResponse() {
        MultipartFile image = new MockMultipartFile("image", "fridge.png", "image/png", VALID_PNG);
        when(aiClient.generateResponse(any(), any())).thenReturn(new AiMessage("model", List.of(new AiMessage.TextPart("{\"listType\":\"NORMAL\",\"items\":[]}"))));

        String result = aiService.extractFromMultimodal(image, "Ce am in frigider?", null, null, TEST_USER_EMAIL);

        assertThat(result).contains("\"listType\":\"NORMAL\"");
    }

    @Test
    void extractFromMultimodal_withLocation_passesContextToTools() {
        MultipartFile image = new MockMultipartFile("image", "fridge.png", "image/png", VALID_PNG);
        UUID itemId = UUID.randomUUID();
        AiMessage toolCallResponse = new AiMessage("model", List.of(
                new AiMessage.ToolCallPart("find_optimal_store", Map.of("item_ids", List.of(itemId.toString())))
        ));
        AiMessage locationResponse = new AiMessage("model", List.of(
                new AiMessage.TextPart("Nearest store suggestion")
        ));
        AiMessage finalResponse = new AiMessage("model", List.of(
                new AiMessage.TextPart("{\"listType\":\"NORMAL\",\"items\":[]}")
        ));
        when(aiClient.generateResponse(any(), any()))
                .thenReturn(toolCallResponse)
                .thenReturn(locationResponse)
                .thenReturn(finalResponse);
        // Updated mock to findOptimalStores which returns a List
        when(storeMatchingEngine.findOptimalStores(45.0, 25.0, 5000, List.of(itemId)))
                .thenReturn(List.of(new StoreMatchResult("store-2", "Store A", 1, 240.0)));

        String result = aiService.extractFromMultimodal(image, "text", 45.0, 25.0, TEST_USER_EMAIL);

        assertThat(result).contains("\"listType\":\"NORMAL\"");
        verify(storeMatchingEngine).findOptimalStores(45.0, 25.0, 5000, List.of(itemId));
    }

    @Test
    void extractFromMultimodal_noImageTextOnly_usesFallbackText() {
        when(aiClient.generateResponse(any(), any())).thenReturn(new AiMessage("model", List.of(new AiMessage.TextPart("result"))));

        String result = aiService.extractFromMultimodal(null, null, null, null, null);

        assertThat(result).isEqualTo("result");
    }

    @Test
    void extractFromMultimodal_whenFinalizerReturnsBlank_fallsBackToRawResponse() {
        AiMessage draftResponse = new AiMessage("model", List.of(new AiMessage.TextPart("raw-json")));
        AiMessage blankFinalizedResponse = new AiMessage("model", List.of(new AiMessage.TextPart("   ")));
        when(aiClient.generateResponse(any(), any()))
                .thenReturn(draftResponse)
                .thenReturn(blankFinalizedResponse);

        String result = aiService.extractFromMultimodal(null, "text", null, null, TEST_USER_EMAIL);

        assertThat(result).isEqualTo("raw-json");
    }

    @Test
    void extractFromMultimodal_whenImageReadFails_throwsAiProcessingException() throws Exception {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getBytes()).thenThrow(new java.io.IOException("disk failure"));

        assertThatThrownBy(() -> aiService.extractFromMultimodal(image, "text", null, null, TEST_USER_EMAIL))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("Error reading image: disk failure");
    }

    @Test
    void detectMimeTypeSecurely_jpeg_returnsImageJpeg() throws Exception {
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        // Updated constructor to include catalogService
        AiService service = new AiService(aiClient, storeMatchingEngine);
        java.lang.reflect.Method method = service.getClass().getDeclaredMethod("detectMimeTypeSecurely", byte[].class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, jpegHeader);

        assertThat(result).isEqualTo("image/jpeg");
    }

    @Test
    void detectMimeTypeSecurely_invalidBytes_returnsNull() throws Exception {
        byte[] invalid = "not-an-image".getBytes();

        // Updated constructor to include catalogService
        AiService service = new AiService(aiClient, storeMatchingEngine);
        java.lang.reflect.Method method = service.getClass().getDeclaredMethod("detectMimeTypeSecurely", byte[].class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, invalid);

        assertThat(result).isNull();
    }
}