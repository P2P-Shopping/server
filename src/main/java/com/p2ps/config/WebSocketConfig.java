package com.p2ps.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures the WebSocket message broker for the application.
 * Enables STOMP routing and sets up the initial communication endpoints.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final StompJwtAuthInterceptor stompJwtAuthInterceptor;
    private final RoomSubscriptionInterceptor subscriptionInterceptor;

    /**
     * Constructor injection for the subscription security interceptor.
     * @param subscriptionInterceptor the interceptor validating inbound traffic
     */
    @Autowired
    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           StompJwtAuthInterceptor stompJwtAuthInterceptor,
                           RoomSubscriptionInterceptor subscriptionInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.stompJwtAuthInterceptor = stompJwtAuthInterceptor;
        this.subscriptionInterceptor = subscriptionInterceptor;
    }

    /**
     * Registers the primary WebSocket endpoint that clients will use to connect.
     * Applies CORS rules and enables SockJS fallback options.
     *
     * @param registry the registry used to configure the STOMP endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    /**
     * Configures the message broker routing prefixes.
     * Defines /topic for server broadcasts and /app for incoming client messages.
     *
     * @param config the registry used to configure the message broker
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers interceptors for inbound client channels.
     * * @param registration the configuration object for the inbound channel
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtAuthInterceptor, subscriptionInterceptor);
    }
}
