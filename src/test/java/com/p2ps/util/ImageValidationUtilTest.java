package com.p2ps.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageValidationUtilTest {

    private final ImageValidationUtil util = new ImageValidationUtil();

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

    private static final byte[] VALID_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x01,
            (byte) 0xFF, (byte) 0xD9
    };

    @Test
    void detectImageFormat_withValidPng_returnsPng() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", VALID_PNG);

        // Act
        String format = util.detectImageFormat(file);

        // Assert
        assertThat(format).isEqualToIgnoringCase("png");
    }

    @Test
    void detectImageFormat_withValidJpeg_returnsJpeg() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", VALID_JPEG);

        // Act
        String format = util.detectImageFormat(file);

        // Assert
        assertThat(format).isEqualToIgnoringCase("jpeg");
    }

    @Test
    void detectImageFormat_withInvalidFile_returnsNull() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Acesta este un text, nu o poza".getBytes());

        // Act
        String format = util.detectImageFormat(file);

        // Assert
        assertThat(format).isNull();
    }

    @Test
    void detectImageFormat_whenIOExceptionOccurs_returnsNull() throws IOException {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getInputStream()).thenThrow(new IOException("Stream error / Disk error"));

        // Act
        String format = util.detectImageFormat(mockFile);

        // Assert
        assertThat(format).isNull();
    }
}