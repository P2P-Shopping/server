package com.p2ps.ai.controller;

import com.p2ps.ai.dto.AiGenerationResponse;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.dto.RecipeRequest;
import com.p2ps.ai.service.AiOrchestrationService;
import com.p2ps.util.ImageValidationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiOrchestrationService orchestration;

    @Mock
    private ImageValidationUtil imageValidator;

    @InjectMocks
    private AiController controller;

    private Principal principal;

    @BeforeEach
    void setUp() {
        principal = () -> "user@test.com";
    }

    @Test
    @SuppressWarnings("unchecked")
    void parseRecipe_returnsGoneStatusAndMigrationMessage() {
        // Arrange
        RecipeRequest req = new RecipeRequest();
        req.setText("recipe text");

        // Act
        ResponseEntity<?> resp = controller.parseRecipe(req);

        // Assert
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(resp.getHeaders().getFirst("Deprecation")).isEqualTo("true");

        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "This endpoint is permanently removed due to the new Gatekeeper architecture.");
        assertThat(body).containsEntry("migration", "Please use the multimodal /api/ai/generate endpoint.");

        verifyNoInteractions(orchestration);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateListMultimodal_whenBothInputsAreEmpty_returnsBadRequest() {
        // Act
        ResponseEntity<?> resp = controller.generateListMultimodal(null, "   ", principal);

        // Assert
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat((Map<String, String>) resp.getBody()).containsEntry("error", "You have to send a text or an image.");
        verifyNoInteractions(orchestration);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateListMultimodal_whenImageIsInvalidFormat_returnsUnsupportedMediaType() {
        // Arrange
        MultipartFile image = new MockMultipartFile("image", "test.gif", "image/gif", "fake-data".getBytes());
        when(imageValidator.detectImageFormat(image)).thenReturn("gif");

        // Act
        ResponseEntity<?> resp = controller.generateListMultimodal(image, null, principal);

        // Assert
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        assertThat((Map<String, String>) resp.getBody()).containsEntry("error", "Invalid file. Only JPEG and PNG images allowed.");
        verifyNoInteractions(orchestration);
    }

    @Test
    void generateListMultimodal_whenImageFormatCannotBeDetected_returnsUnsupportedMediaType() {
        // Arrange
        MultipartFile image = new MockMultipartFile("image", "corrupted.jpg", "image/jpeg", "fake-data".getBytes());
        when(imageValidator.detectImageFormat(image)).thenReturn(null);

        // Act
        ResponseEntity<?> resp = controller.generateListMultimodal(image, "am si text", principal);

        // Assert
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        verifyNoInteractions(orchestration);
    }

    @Test
    void generateListMultimodal_whenValidTextOnly_returnsOk() {
        // Arrange
        AiGenerationResponse aiResp = new AiGenerationResponse();
        aiResp.setListType("RECIPE");
        when(orchestration.generateShoppingItems(null, "Valid recipe text")).thenReturn(aiResp);

        // Act
        ResponseEntity<?> resp = controller.generateListMultimodal(null, "Valid recipe text", principal);

        // Assert
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(aiResp);

        verify(imageValidator, never()).detectImageFormat(any());
    }

    @Test
    void generateListMultimodal_whenValidImageAndText_returnsOk() {
        // Arrange
        MultipartFile image = new MockMultipartFile("image", "photo.png", "image/png", "fake-data".getBytes());
        when(imageValidator.detectImageFormat(image)).thenReturn("png");

        AiGenerationResponse aiResp = new AiGenerationResponse();
        aiResp.setListType("FREQUENT");
        when(orchestration.generateShoppingItems(image, "What is this?")).thenReturn(aiResp);

        // Act
        ResponseEntity<?> resp = controller.generateListMultimodal(image, "What is this?", principal);

        // Assert
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(aiResp);
        verify(imageValidator, times(1)).detectImageFormat(image);
        verify(orchestration, times(1)).generateShoppingItems(image, "What is this?");
    }
}