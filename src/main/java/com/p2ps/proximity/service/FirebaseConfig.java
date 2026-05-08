package com.p2ps.proximity.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Initializes the Firebase Admin SDK on application startup.
 *
 * Requires a Firebase service account JSON file on the classpath.
 * Set the path via the 'firebase.service-account-path' property.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.service-account-path:firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[FIREBASE] Firebase already initialized, skipping.");
            return;
        }

        try {
            InputStream serviceAccount = new ClassPathResource(serviceAccountPath).getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("[FIREBASE] Firebase Admin SDK initialized successfully.");

        } catch (IOException e) {
            log.error("[FIREBASE] Failed to initialize Firebase Admin SDK. " +
                            "Ensure '{}' exists on the classpath. Push notifications will not work. Error: {}",
                    serviceAccountPath, e.getMessage());
        }
    }
}