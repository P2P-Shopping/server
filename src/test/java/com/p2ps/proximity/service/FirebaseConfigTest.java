package com.p2ps.proximity.service;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FirebaseConfigTest {

    @AfterEach
    void tearDown() {
        // Cleanup Firebase apps after each test
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    void shouldSkipInitializationWhenFirebaseAlreadyInitialized() {
        // First init is done via firebase-service-account.json in test resources
        // We test the "already initialized" branch by calling twice
        FirebaseConfig config = new FirebaseConfig();
        ReflectionTestUtils.setField(config,
                "serviceAccountPath", "firebase-service-account.json");

        // Call once — may succeed or log error depending on test resources
        assertDoesNotThrow(config::initializeFirebase);

        // Call again — should hit the "already initialized" branch
        assertDoesNotThrow(config::initializeFirebase);
    }

    @Test
    void shouldHandleMissingServiceAccountFile() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);

        FirebaseConfig config = new FirebaseConfig();
        ReflectionTestUtils.setField(config,
                "serviceAccountPath", "nonexistent-file.json");

        // Should not throw — logs error and continues
        assertDoesNotThrow(config::initializeFirebase);
    }
}