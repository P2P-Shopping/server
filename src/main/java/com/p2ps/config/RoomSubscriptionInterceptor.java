package com.p2ps.config;

import com.p2ps.auth.security.JwtAuthFilter;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
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
@NullUnmarked
public class RoomSubscriptionInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RoomSubscriptionInterceptor.class);

    private static final Pattern VALID_LIST_ID = Pattern.compile("^[a-zA-Z0-9-]+$");
    private static final String INVITATIONS_TOPIC_PREFIX = "/topic/invitations/";

    private final ShoppingListRepository shoppingListRepository;
    private final JwtAuthFilter jwtAuthFilter;

    public RoomSubscriptionInterceptor(ShoppingListRepository shoppingListRepository, JwtAuthFilter jwtAuthFilter) {
        this.shoppingListRepository = shoppingListRepository;
        this.jwtAuthFilter = jwtAuthFilter;
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
    @SuppressWarnings("java:S2638")
    public @Nullable Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        String destination = accessor != null ? accessor.getDestination() : null;

        if (isSubscribeToTopic(accessor, destination) && !handleSubscription(accessor, destination)) {
            return null;
        }
        return message;
    }

    private boolean isSubscribeToTopic(StompHeaderAccessor accessor, String destination) {
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand()) || destination == null) {
            return false;
        }
        return destination.startsWith("/topic/list/") || destination.startsWith(INVITATIONS_TOPIC_PREFIX);
    }

    private boolean handleSubscription(StompHeaderAccessor accessor, String destination) {
        Authentication auth = getAuthenticatedUser(accessor);
        if (auth == null) {
            logger.error("Security Alert: Blocked subscription attempt to {} without authenticated principal", destination);
            return false;
        }

        if (destination.startsWith(INVITATIONS_TOPIC_PREFIX)) {
            return handleInvitationSubscription(destination, auth);
        }

        String extractedId = extractListId(destination);
        if (!VALID_LIST_ID.matcher(extractedId).matches()) {
            logger.warn("Security Alert: Blocked malformed room subscription attempt");
            return false;
        }

        return validateUserAccess(extractedId, auth.getName());
    }

    private boolean handleInvitationSubscription(String destination, Authentication auth) {
        String requestedEmail = destination.substring(INVITATIONS_TOPIC_PREFIX.length());
        if (requestedEmail.isEmpty()) {
            logger.warn("Security Alert: Blocked invitation subscription with empty email");
            return false;
        }
        if (!requestedEmail.equals(auth.getName())) {
            logger.warn("Security Alert: User {} attempted to subscribe to invitation topic of {}", auth.getName(), requestedEmail);
            return false;
        }
        return true;
    }

    private Authentication getAuthenticatedUser(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication auth && auth.isAuthenticated()) {
            return auth;
        }

        // Fallback: Check session attributes for the token and re-authenticate.
        // This is necessary when using native WebSockets where the principal might not be fully propagated to all interceptors.
        if (accessor.getSessionAttributes() != null) {
            String token = (String) accessor.getSessionAttributes().get(JwtHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE);
            if (token != null) {
                return jwtAuthFilter.authenticateToken(token);
            }
        }

        return null;
    }

    private String extractListId(String destination) {
        String extractedPath = destination.substring("/topic/list/".length());
        if (extractedPath.endsWith("/presence")) {
            return extractedPath.substring(0, extractedPath.length() - "/presence".length());
        }
        if (extractedPath.endsWith("/members")) {
            return extractedPath.substring(0, extractedPath.length() - "/members".length());
        }
        return extractedPath;
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
