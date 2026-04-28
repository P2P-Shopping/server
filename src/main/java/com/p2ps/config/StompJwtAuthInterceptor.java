package com.p2ps.config;

import com.p2ps.auth.security.JwtAuthFilter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StompJwtAuthInterceptor implements ChannelInterceptor {

    private final JwtAuthFilter jwtAuthFilter;

    public StompJwtAuthInterceptor(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Override
    @SuppressWarnings("java:S2638")
    public @Nullable Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            UsernamePasswordAuthenticationToken authentication = resolveAuthentication(accessor);

            if (authentication != null) {
                accessor.setUser(authentication);
                return MessageBuilder.fromMessage(message)
                        .setHeader(SimpMessageHeaderAccessor.USER_HEADER, authentication)
                        .build();
            }

            return message;
        }

        return message;
    }

    private UsernamePasswordAuthenticationToken resolveAuthentication(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            return null;
        }

        UsernamePasswordAuthenticationToken authentication = jwtAuthFilter.authenticateToken(token);
        if (authentication == null) {
            throw new BadCredentialsException("Invalid JWT token");
        }
        return authentication;
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
        return sessionToken instanceof String string ? string : null;
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
