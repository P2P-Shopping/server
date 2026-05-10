package com.p2ps.proximity.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FcmServiceTest {

    private final FcmService fcmService = new FcmService();

    @Test
    void shouldSkipNotificationWhenFirebaseNotInitialized() {
        try (MockedStatic<FirebaseApp> mockedFirebaseApp = mockStatic(FirebaseApp.class)) {
            mockedFirebaseApp.when(FirebaseApp::getApps).thenReturn(Collections.emptyList());

            fcmService.sendProximityAlert(
                    "fcm-token-abc",
                    "Item nearby!",
                    "body",
                    "http://localhost/list/1"
            );

            // No exception should be thrown
        }
    }

    @Test
    void shouldSendNotificationSuccessfully() throws FirebaseMessagingException {
        FirebaseApp mockApp = mock(FirebaseApp.class);
        FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
        when(mockMessaging.send(any(Message.class))).thenReturn("message-id-123");

        try (MockedStatic<FirebaseApp> mockedFirebaseApp = mockStatic(FirebaseApp.class);
             MockedStatic<FirebaseMessaging> mockedMessaging = mockStatic(FirebaseMessaging.class)) {

            mockedFirebaseApp.when(FirebaseApp::getApps).thenReturn(List.of(mockApp));
            mockedMessaging.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

            fcmService.sendProximityAlert(
                    "fcm-token-abc",
                    "Item nearby!",
                    "body",
                    "http://localhost/list/1"
            );

            verify(mockMessaging).send(any(Message.class));
        }
    }

    @Test
    void shouldHandleFirebaseMessagingException() throws FirebaseMessagingException {
        FirebaseApp mockApp = mock(FirebaseApp.class);
        FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(mockMessaging.send(any(Message.class))).thenThrow(exception);

        try (MockedStatic<FirebaseApp> mockedFirebaseApp = mockStatic(FirebaseApp.class);
             MockedStatic<FirebaseMessaging> mockedMessaging = mockStatic(FirebaseMessaging.class)) {

            mockedFirebaseApp.when(FirebaseApp::getApps).thenReturn(List.of(mockApp));
            mockedMessaging.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

            // Should not throw - exception is caught and logged
            fcmService.sendProximityAlert(
                    "fcm-token-abc",
                    "Item nearby!",
                    "body",
                    "http://localhost/list/1"
            );

            verify(mockMessaging).send(any(Message.class));
        }
    }
}