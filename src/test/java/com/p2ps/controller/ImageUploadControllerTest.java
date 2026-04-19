package com.p2ps.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageUploadControllerTest {

    private ImageUploadController controller;

    @BeforeEach
    void setUp() {
        controller = new ImageUploadController();
    }

    public static byte[] createImageBytes(String formatName) throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, formatName, baos);
            return baos.toByteArray();
        }
    }

    @Test
    void uploadImage_shouldReturnOk_forValidJpeg() throws Exception {
        byte[] bytes = createImageBytes("jpg");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recipe.jpg",
            "application/octet-stream",
                bytes
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Image uploaded successfully", response.getBody().get("message"));
        assertEquals("recipe.jpg", response.getBody().get("fileName"));
        assertEquals("image/jpeg", response.getBody().get("contentType"));
        assertEquals((long) bytes.length, response.getBody().get("size"));
    }

    @Test
    void uploadImage_shouldReturnOk_forValidPng() throws Exception {
        byte[] bytes = createImageBytes("png");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recipe.png",
            "text/plain",
                bytes
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Image uploaded successfully", response.getBody().get("message"));
        assertEquals("recipe.png", response.getBody().get("fileName"));
        assertEquals("image/png", response.getBody().get("contentType"));
        assertEquals((long) bytes.length, response.getBody().get("size"));
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
        assertEquals("File is empty", response.getBody().get("message"));
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
        assertEquals("Invalid image file", response.getBody().get("error"));
        assertEquals("Invalid image file", response.getBody().get("message"));
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
        assertEquals("Invalid image file", response.getBody().get("error"));
        assertEquals("Invalid image file", response.getBody().get("message"));
    }

    @Test
    void uploadImage_shouldUseUnknown_whenOriginalFilenameIsNull() throws Exception {
        byte[] bytes = createImageBytes("jpg");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                null,
                "image/jpeg",
                bytes
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("unknown", response.getBody().get("fileName"));
    }

    @Test
    void uploadImage_shouldReturnUnsupportedMediaType_forInvalidImageBytesEvenIfContentTypeImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notimage.png",
                "image/png",
                "not-an-image".getBytes()
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid image file", response.getBody().get("error"));
        assertEquals("Invalid image file", response.getBody().get("message"));
    }

    @Test
    void uploadImage_shouldReturnUnsupportedMediaType_forDetectedUnsupportedFormat() throws Exception {
        byte[] bytes = createImageBytes("bmp");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recipe.bmp",
                "image/bmp",
                bytes
        );

        ResponseEntity<Map<String, Object>> response = controller.uploadImage(file);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Only JPEG and PNG are allowed", response.getBody().get("error"));
        assertEquals("Only JPEG and PNG are allowed", response.getBody().get("message"));
    }
}

class ImageUploadControllerMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ImageUploadController()).build();
    }

    @Test
    void multipartEndpoint_shouldAcceptImage() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "upload.png",
                "image/png",
                ImageUploadControllerTest.createImageBytes("png")
        );

        mockMvc.perform(multipart("/api/images/upload").file(multipartFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("upload.png"));
    }
}