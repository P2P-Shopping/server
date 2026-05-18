package com.p2ps.config;

import com.p2ps.dto.PresenceEvent;
import com.p2ps.service.PresenceStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Listens for and logs WebSocket connection lifecycle events.
 * Monitors when clients establish or drop their STOMP sessions.
 */
@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final PresenceStateService presenceStateService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventListener(PresenceStateService presenceStateService, SimpMessagingTemplate messagingTemplate) {
        this.presenceStateService = presenceStateService;
        this.messagingTemplate = messagingTemplate;
    }

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
     * Removes the user from the room roster if they abruptly disconnected.
     *
     * @param event the session disconnect event triggered when a client drops
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String sessionPreview = sessionId == null ? "unknown" : sessionId.substring(0, Math.min(8, sessionId.length()));
        logger.info("User disconnected. Session ID prefix: {}", sessionPreview);

        if (sessionId != null) {
            PresenceEvent sessionEvent = presenceStateService.getSessionTracker().remove(sessionId);
            if (sessionEvent != null) {
                String listId = sessionEvent.getListId();
                String username = sessionEvent.getUsername();
                Set<String> roster = presenceStateService.getRoomRosters().get(listId);
                Map<String, String> names = presenceStateService.getRoomDisplayNames().get(listId);
                
                if (roster != null) {
                    roster.remove(username);
                    if (names != null) {
                        names.remove(username);
                    }

                    PresenceEvent rosterUpdate = new PresenceEvent();
                    rosterUpdate.setEventType(PresenceEvent.EventType.ROSTER_UPDATE);
                    rosterUpdate.setListId(listId);
                    rosterUpdate.setActiveUsers(new java.util.HashSet<>(roster));
                    rosterUpdate.setDisplayNames(new HashMap<>(names != null ? names : new java.util.HashMap<>()));
                    
                    logger.debug("Broadcasting ROSTER_UPDATE on disconnect for list {}", listId);
                    messagingTemplate.convertAndSend("/topic/list/" + listId + "/presence", rosterUpdate);
                }
            }
        }
    }
}
