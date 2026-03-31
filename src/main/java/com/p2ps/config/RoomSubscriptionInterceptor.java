package com.p2ps.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Security interceptor for inbound WebSocket traffic.
 * Validates STOMP SUBSCRIBE frames to prevent unauthorized access or malformed topic creation.
 */
@Component
public class RoomSubscriptionInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RoomSubscriptionInterceptor.class);

    /**
     * Inspects inbound messages before they are processed by the message broker.
     * Enforces strict formatting rules on requested room IDs.
     *
     * @param message the inbound WebSocket message
     * @param channel the channel the message is traveling on
     * @return the unmodified message if valid, or throws an exception if invalid
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            
            if (destination != null && destination.startsWith("/topic/list/")) {
                String listId = destination.substring("/topic/list/".length());
                
                // Security Check: Only allow alphanumeric list IDs (plus hyphens). Prevents directory traversal or wildcard injection.
                if (!listId.matches("^[a-zA-Z0-9-]+$")) {
                    logger.warn("Security Alert: Blocked malformed room subscription attempt");
                    throw new IllegalArgumentException("Invalid List ID format");
                }
            }
        }
        return message;
    }
}
