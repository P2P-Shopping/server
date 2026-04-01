package com.p2ps.config;

import com.p2ps.auth.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtHandshakeInterceptorTest {

    @Test
    void beforeHandshake_AllowsValidQueryToken() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(jwtAuthFilter);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        HashMap<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create("https://example.com/ws?token=valid-token"));

        when(jwtAuthFilter.authenticateToken("valid-token")).thenReturn(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user@test.com", null, java.util.List.of()));

        boolean allowed = interceptor.beforeHandshake(request, response, handler, attributes);

        assertTrue(allowed);
        assertEquals("valid-token", attributes.get(JwtHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE));
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void beforeHandshake_RejectsInvalidQueryToken() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        JwtHandshakeInterceptor interceptor = new JwtHandshakeInterceptor(jwtAuthFilter);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        HashMap<String, Object> attributes = new HashMap<>();

        when(request.getURI()).thenReturn(URI.create("https://example.com/ws?token=bad-token"));

        when(jwtAuthFilter.authenticateToken("bad-token")).thenReturn(null);

        boolean allowed = interceptor.beforeHandshake(request, response, handler, attributes);

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
