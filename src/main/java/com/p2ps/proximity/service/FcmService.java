package com.p2ps.proximity.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for dispatching Firebase Cloud Messaging (FCM) push notifications.
 * Each notification is sent to a specific device token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    /**
     * Sends a proximity alert notification to a specific device.
     *
     * @param fcmToken  the device's FCM registration token
     * @param title     notification title shown in the status bar
     * @param body      notification body text
     * @param deepLink  URL to open inside the WebView when the user taps the notification
     */
    public void sendProximityAlert(String fcmToken, String title, String body, String deepLink) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("deepLink", deepLink)
                .putData("type", "PROXIMITY_ALERT")
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM] Notification sent successfully. Message ID: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("[FCM] Failed to send notification to token {}: {}", fcmToken, e.getMessage(), e);
        }
    }
}
