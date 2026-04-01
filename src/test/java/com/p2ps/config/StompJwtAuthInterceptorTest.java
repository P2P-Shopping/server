package com.p2ps.config;

import com.p2ps.auth.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
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
    void preSend_ConnectWithoutTokenPassesThrough() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertNull(StompHeaderAccessor.wrap(result).getUser());
        verify(jwtAuthFilter, never()).authenticateToken(any());
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

    @Test
    void preSend_NonConnectCommandsPassThrough() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);

        for (StompCommand command : new StompCommand[]{StompCommand.SUBSCRIBE, StompCommand.SEND, StompCommand.DISCONNECT, StompCommand.UNSUBSCRIBE}) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
            accessor.setLeaveMutable(true);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            MessageChannel channel = mock(MessageChannel.class);

            Message<?> result = interceptor.preSend(message, channel);

            assertSame(message, result, "Expected same message for " + command);
        }
    }

    @Test
    void preSend_ConnectWithLowercaseAuthorizationHeader() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("authorization", "Bearer lower-token");
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());

        when(jwtAuthFilter.authenticateToken(anyString())).thenAnswer(invocation -> {
            assertEquals("lower-token", invocation.getArgument(0));
            return authentication;
        });

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertSame(authentication, StompHeaderAccessor.wrap(result).getUser());
    }

    @Test
    void preSend_ConnectWithTokenHeader() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("token", "token-header-value");
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());

        when(jwtAuthFilter.authenticateToken(anyString())).thenAnswer(invocation -> {
            assertEquals("token-header-value", invocation.getArgument(0));
            return authentication;
        });

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertSame(authentication, StompHeaderAccessor.wrap(result).getUser());
    }

    @Test
    void preSend_ConnectWithAccessTokenHeader() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("access_token", "access-token-value");
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());

        when(jwtAuthFilter.authenticateToken(anyString())).thenAnswer(invocation -> {
            assertEquals("access-token-value", invocation.getArgument(0));
            return authentication;
        });

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertSame(authentication, StompHeaderAccessor.wrap(result).getUser());
    }

    @Test
    void preSend_ConnectWithNullSessionAttributes() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertNull(StompHeaderAccessor.wrap(result).getUser());
        verify(jwtAuthFilter, never()).authenticateToken(any());
    }

    @Test
    void preSend_ConnectWithNonStringSessionToken() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>(java.util.Map.of(JwtHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE, 12345)));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertNull(StompHeaderAccessor.wrap(result).getUser());
        verify(jwtAuthFilter, never()).authenticateToken(any());
    }

    @Test
    void preSend_ConnectWithInvalidTokenThrowsException() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        StompJwtAuthInterceptor interceptor = new StompJwtAuthInterceptor(jwtAuthFilter);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer expired-token");
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        when(jwtAuthFilter.authenticateToken(anyString())).thenReturn(null);

        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(message, channel));
    }
}
