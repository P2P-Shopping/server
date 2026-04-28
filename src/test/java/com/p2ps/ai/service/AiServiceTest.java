package com.p2ps.ai.service;

import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.exception.AiProcessingException;
import com.p2ps.service.StoreMatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private CatalogService catalogService;

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

    @BeforeEach
    void setUp() throws Exception {
        aiService = new AiService(aiClient, catalogService, storeMatchingEngine);

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
    void extractFromMultimodal_withInvalidImage_throwsException() {
        MultipartFile fakeImage = new MockMultipartFile("image", "virus.png", "image/png", "fake-pixel-data".getBytes());

        assertThatThrownBy(() -> aiService.extractFromMultimodal(fakeImage, "text", null, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("Unsupported or corrupted image format");
    }

    @Test
    void extractFromMultimodal_withImageAndText_returnsTextResponse() {
        MultipartFile image = new MockMultipartFile("image", "fridge.png", "image/png", VALID_PNG);
        when(aiClient.generateResponse(any(), any())).thenReturn(new AiMessage("model", List.of(new AiMessage.TextPart("{\"listType\":\"NORMAL\",\"items\":[]}"))));

        String result = aiService.extractFromMultimodal(image, "Ce am in frigider?", null, null);

        assertThat(result).contains("\"listType\":\"NORMAL\"");
    }
}
