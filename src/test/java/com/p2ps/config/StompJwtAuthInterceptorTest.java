package com.p2ps.config;

import com.p2ps.auth.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompJwtAuthInterceptorTest {

    @Test
    void preSend_ConnectWithHeaderTokenAuthenticates() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());

        when(jwtAuthFilter.authenticateToken(anyString())).thenAnswer(invocation -> {
            assertEquals("valid-token", invocation.getArgument(0));
            return authentication;
        });

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertSame(authentication, StompHeaderAccessor.wrap(result).getUser());
    }

    @Test
    void preSend_ConnectWithoutTokenFails() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void preSend_ConnectUsesSessionTokenWhenPresent() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>(java.util.Map.of(JwtHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE, "query-token")));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());

        when(jwtAuthFilter.authenticateToken(anyString())).thenAnswer(invocation -> {
            assertEquals("query-token", invocation.getArgument(0));
            return authentication;
        });

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertSame(authentication, StompHeaderAccessor.wrap(result).getUser());
    }
}
