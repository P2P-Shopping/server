package com.p2ps.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImageUploadControllerTest {

    private ImageUploadController controller;

    @BeforeEach
    void setUp() {
        controller = new ImageUploadController();
    }

    @Test
    void uploadImage_shouldReturnOk_forValidJpeg() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recipe.jpg",
                "image/jpeg",
                "fake-image-content".getBytes()
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Image uploaded successfully", response.getBody().get("message"));
        assertEquals("recipe.jpg", response.getBody().get("fileName"));
        assertEquals("image/jpeg", response.getBody().get("contentType"));
        assertEquals((long) "fake-image-content".getBytes().length, response.getBody().get("size"));
    }

    @Test
    void uploadImage_shouldReturnOk_forValidPng() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recipe.png",
                "image/png",
                "fake-png-content".getBytes()
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Image uploaded successfully", response.getBody().get("message"));
        assertEquals("recipe.png", response.getBody().get("fileName"));
        assertEquals("image/png", response.getBody().get("contentType"));
    }

    @Test
    void uploadImage_shouldReturnBadRequest_forEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("File is empty", response.getBody().get("error"));
    }

    @Test
    void uploadImage_shouldReturnUnsupportedMediaType_forInvalidType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "not-an-image".getBytes()
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Only JPEG and PNG are allowed", response.getBody().get("error"));
    }

    @Test
    void uploadImage_shouldReturnUnsupportedMediaType_whenContentTypeIsNull() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unknown.bin",
                null,
                "binary".getBytes()
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Only JPEG and PNG are allowed", response.getBody().get("error"));
    }
}