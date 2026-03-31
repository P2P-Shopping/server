package com.p2ps.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listens for and logs WebSocket connection lifecycle events.
 * Monitors when clients establish or drop their STOMP sessions.
 */
@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    /**
     * Handles successful WebSocket connection events.
     *
     * @param event the session connected event triggered by a new client
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        logger.info("New web socket connection established.");
    }

    /**
     * Handles WebSocket disconnection events.
     * Extracts and logs a masked portion of the session ID for security tracking.
     *
     * @param event the session disconnect event triggered when a client drops
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String sessionPreview = sessionId == null ? "unknown" : sessionId.substring(0, Math.min(8, sessionId.length()));
        logger.info("User disconnected. Session ID prefix: {}", sessionPreview);
    }
}