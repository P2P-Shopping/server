package com.p2ps.proximity.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FcmServiceTest {

    private final FcmService fcmService = new FcmService();

    @Test
    void shouldSendNotificationSuccessfully() throws FirebaseMessagingException {
        FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
        when(mockMessaging.send(any(Message.class))).thenReturn("message-id-123");

        try (MockedStatic<FirebaseMessaging> mockedStatic =
                     mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

            fcmService.sendProximityAlert(
                    "fcm-token-abc",
                    "Item nearby!",
                    "A shopping list item is available near your current location.",
                    "http://localhost:5173/list/list-001"
            );

            verify(mockMessaging).send(any(Message.class));
        }
    }

    @Test
    void shouldHandleFirebaseMessagingException() throws FirebaseMessagingException {
        FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(mockMessaging.send(any(Message.class))).thenThrow(exception);

        try (MockedStatic<FirebaseMessaging> mockedStatic =
                     mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

            // Should not throw — exception is caught and logged
            fcmService.sendProximityAlert(
                    "fcm-token-abc",
                    "Item nearby!",
                    "A shopping list item is available near your current location.",
                    "http://localhost:5173/list/list-001"
            );

            verify(mockMessaging).send(any(Message.class));
        }
    }
}