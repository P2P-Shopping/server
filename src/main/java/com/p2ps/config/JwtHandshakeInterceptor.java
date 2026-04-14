package com.p2ps.config;

import com.p2ps.auth.security.JwtAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SESSION_TOKEN_ATTRIBUTE = "wsJwtToken";

    private static final Logger logger = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtAuthFilter jwtAuthFilter;
    private final boolean enableUrlToken;

    public JwtHandshakeInterceptor(JwtAuthFilter jwtAuthFilter,
                                   @Value("${websocket.compatibility.enableUrlToken:false}") boolean enableUrlToken) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.enableUrlToken = enableUrlToken;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!enableUrlToken) {
            return true;
        }

        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");

        if (token == null || token.isBlank()) {
            return true;
        }

        if (jwtAuthFilter.authenticateToken(token) == null) {
            logger.warn("Rejecting websocket handshake with invalid JWT query token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(SESSION_TOKEN_ATTRIBUTE, token);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // No-op.
    }
}
