package com.p2ps.proximity.service;

import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class FirebaseConfigTest {

    @Test
    void shouldSkipInitializationWhenFirebaseAlreadyInitialized() {
        FirebaseApp mockApp = mock(FirebaseApp.class);

        try (MockedStatic<FirebaseApp> mockedFirebaseApp = mockStatic(FirebaseApp.class)) {
            mockedFirebaseApp.when(FirebaseApp::getApps).thenReturn(List.of(mockApp));

            FirebaseConfig config = new FirebaseConfig();
            ReflectionTestUtils.setField(config, "serviceAccountPath",
                    "firebase-service-account.json");

            assertDoesNotThrow(config::initializeFirebase);

            // Verify initializeApp was NEVER called because Firebase is already initialized
            mockedFirebaseApp.verify(() -> FirebaseApp.initializeApp(any(FirebaseOptions.class)), never());
        }
    }

    @Test
    void shouldHandleMissingServiceAccountFile() {
        try (MockedStatic<FirebaseApp> mockedFirebaseApp = mockStatic(FirebaseApp.class)) {
            mockedFirebaseApp.when(FirebaseApp::getApps).thenReturn(Collections.emptyList());

            FirebaseConfig config = new FirebaseConfig();
            ReflectionTestUtils.setField(config, "serviceAccountPath", "nonexistent-file.json");

            // Should not throw - logs error and continues
            assertDoesNotThrow(config::initializeFirebase);
        }
    }
}