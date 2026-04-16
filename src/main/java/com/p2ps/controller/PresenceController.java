package com.p2ps.controller;

import com.p2ps.dto.PresenceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Controller responsible for routing low-latency presence events without hitting the database.
 * Facilitates events such as JOIN, LEAVE, and TYPING across the real-time application.
 */
@Controller
public class PresenceController {

    private static final Logger logger = LoggerFactory.getLogger(PresenceController.class);

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Constructs a new PresenceController.
     *
     * @param messagingTemplate the SimpMessagingTemplate used to broadcast presence events
     */
    public PresenceController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles incoming presence payloads and broadcasts them to the active room.
     * Logged safely using debug and metadata mapping.
     *
     * @param listId the specific list ID for targeting the broadcast topic
     * @param payload the presence payload containing user action and data
     */
    @MessageMapping("/list/{listId}/presence")
    public void handlePresenceEvent(@DestinationVariable String listId, PresenceEvent payload) {
        if (logger.isDebugEnabled()) {
            logger.debug("Routing presence event type {} for room length {}", 
                payload.getEventType() != null ? payload.getEventType().name() : "UNKNOWN", 
                listId != null ? listId.length() : 0);
        }
        
        // Prevent raw logging of user inputs based on CI/CD constraints
        
        messagingTemplate.convertAndSend("/topic/list/" + listId + "/presence", payload);
    }
}
