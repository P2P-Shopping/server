package com.p2ps.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Security interceptor for inbound WebSocket traffic.
 * Validates STOMP SUBSCRIBE frames to prevent unauthorized access or malformed topic creation.
 */
@Component
public class RoomSubscriptionInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RoomSubscriptionInterceptor.class);

    private static final Pattern VALID_LIST_ID = Pattern.compile("^[a-zA-Z0-9-]+$");

    /**
     * Inspects inbound messages before they are processed by the message broker.
     * Enforces strict formatting rules on requested room IDs.
     *
     * @param message the inbound WebSocket message
     * @param channel the channel the message is traveling on
     * @return the unmodified message if valid, or null to drop the message
     */
    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            
            if (destination != null && destination.startsWith("/topic/list/")) {
                String listId = destination.substring("/topic/list/".length());
                
                // Security Check: Only allow alphanumeric list IDs (plus hyphens). Prevents directory traversal or wildcard injection.
                if (!VALID_LIST_ID.matcher(listId).matches()) {
                    logger.warn("Security Alert: Blocked malformed room subscription attempt");
                    return null;
                }
            }
        }
        return message;
    }
}
