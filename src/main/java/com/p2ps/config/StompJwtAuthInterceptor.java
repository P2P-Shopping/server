package com.p2ps.config;

import com.p2ps.auth.security.JwtAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StompJwtAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(StompJwtAuthInterceptor.class);

    private final JwtAuthFilter jwtAuthFilter;

    public StompJwtAuthInterceptor(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            UsernamePasswordAuthenticationToken authentication = resolveAuthentication(accessor);

            if (authentication == null) {
                SecurityContextHolder.clearContext();
                logger.warn("Rejecting websocket CONNECT without a valid JWT");
                throw new AccessDeniedException("WebSocket JWT authentication failed");
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);
            return org.springframework.messaging.support.MessageBuilder.fromMessage(message)
                    .setHeader(SimpMessageHeaderAccessor.USER_HEADER, authentication)
                    .build();
        }

        return message;
    }

    private UsernamePasswordAuthenticationToken resolveAuthentication(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        return jwtAuthFilter.authenticateToken(token);
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String headerToken = accessor.getFirstNativeHeader("Authorization");
        if (headerToken == null) {
            headerToken = accessor.getFirstNativeHeader("authorization");
        }
        if (headerToken == null) {
            headerToken = accessor.getFirstNativeHeader("token");
        }
        if (headerToken == null) {
            headerToken = accessor.getFirstNativeHeader("access_token");
        }

        if (headerToken != null) {
            return extractBearerToken(headerToken);
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }

        Object sessionToken = sessionAttributes.get(JwtHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE);
        return sessionToken instanceof String ? (String) sessionToken : null;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        String token = authorizationHeader.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return token.substring(7).trim();
        }

        return token;
    }

}
