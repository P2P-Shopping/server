package com.p2ps.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
public class ImageUploadController {

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "File is empty"
            ));
        }
        // Inspect the file contents to determine if it's a real image and its format
        String format = detectImageFormat(file);
        if (format == null) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of(
                    "error", "Invalid image file"
            ));
        }

        String fmtLower = format.toLowerCase();
        if (!fmtLower.equals("jpeg") && !fmtLower.equals("jpg") && !fmtLower.equals("png")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of(
                    "error", "Only JPEG and PNG are allowed"
            ));
        }

        String original = file.getOriginalFilename();
        String safeFileName = (original == null || original.isBlank()) ? "unknown" : original;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        return ResponseEntity.ok(Map.of(
                "message", "Image uploaded successfully",
                "fileName", safeFileName,
                "contentType", contentType,
                "size", file.getSize()
        ));
    }

    private String detectImageFormat(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            String format = reader.getFormatName();
            reader.dispose();
            return format;
        } catch (IOException e) {
            return null;
        }
    }
}
