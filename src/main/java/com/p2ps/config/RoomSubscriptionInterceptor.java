package com.p2ps.config;

import com.p2ps.lists.repo.ShoppingListRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Security interceptor for inbound WebSocket traffic.
 * Validates STOMP SUBSCRIBE frames to prevent unauthorized access or malformed topic creation.
 */
@Component
public class RoomSubscriptionInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RoomSubscriptionInterceptor.class);

    private static final Pattern VALID_LIST_ID = Pattern.compile("^[a-zA-Z0-9-]+$");

    private final ShoppingListRepository shoppingListRepository;

    public RoomSubscriptionInterceptor(ShoppingListRepository shoppingListRepository) {
        this.shoppingListRepository = shoppingListRepository;
    }

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
        
        if (isSubscribeToTopic(accessor)) {
            if (!handleSubscription(accessor)) {
                return null;
            }
        }
        return message;
    }

    private boolean isSubscribeToTopic(StompHeaderAccessor accessor) {
        return accessor != null && 
               StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && 
               accessor.getDestination() != null && 
               accessor.getDestination().startsWith("/topic/list/");
    }

    private boolean handleSubscription(StompHeaderAccessor accessor) {
        Authentication auth = getAuthenticatedUser(accessor);
        if (auth == null) {
            logger.warn("Security Alert: Blocked subscription attempt without authenticated principal");
            return false;
        }

        String extractedId = extractListId(accessor.getDestination());
        if (!VALID_LIST_ID.matcher(extractedId).matches()) {
            logger.warn("Security Alert: Blocked malformed room subscription attempt");
            return false;
        }

        return validateUserAccess(extractedId, auth.getName());
    }

    private Authentication getAuthenticatedUser(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication auth && auth.isAuthenticated()) {
            return auth;
        }
        return null;
    }

    private String extractListId(String destination) {
        String extractedPath = destination.substring("/topic/list/".length());
        return extractedPath.endsWith("/presence") ? 
               extractedPath.substring(0, extractedPath.length() - "/presence".length()) : 
               extractedPath;
    }

    private boolean validateUserAccess(String extractedId, String userEmail) {
        try {
            UUID listId = UUID.fromString(extractedId);
            boolean hasAccess = shoppingListRepository.existsByIdAndUserEmailOrCollaboratorEmail(listId, userEmail);

            if (!hasAccess) {
                logger.warn("Security Alert: User {} attempted to subscribe to unauthorized list {}", userEmail, extractedId);
                return false;
            }
            return true;
        } catch (IllegalArgumentException _) {
            logger.warn("Security Alert: Blocked subscription attempt with invalid UUID format: {}", extractedId);
            return false;
        }
    }
}
