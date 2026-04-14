package com.p2ps.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    private JwtHandshakeInterceptor handshakeInterceptor;
    private StompJwtAuthInterceptor stompInterceptor;
    private RoomSubscriptionInterceptor interceptor;
    private WebSocketConfig config;

    @BeforeEach
    void setUp() {
        handshakeInterceptor = mock(JwtHandshakeInterceptor.class);
        stompInterceptor = mock(StompJwtAuthInterceptor.class);
        interceptor = mock(RoomSubscriptionInterceptor.class);
        config = new WebSocketConfig(handshakeInterceptor, stompInterceptor, interceptor);
    }

    @Test
    void registerStompEndpoints() throws Exception {
        String[] allowedOrigins = {"https://example.com"};
        Field field = WebSocketConfig.class.getDeclaredField("allowedOrigins");
        field.setAccessible(true);
        field.set(config, allowedOrigins);

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration reg = mock(StompWebSocketEndpointRegistration.class);

        when(registry.addEndpoint(anyString())).thenReturn(reg);
        when(reg.addInterceptors(any())).thenReturn(reg);
        when(reg.setAllowedOriginPatterns(any())).thenReturn(reg);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(reg).addInterceptors(handshakeInterceptor);
        verify(reg).setAllowedOriginPatterns(allowedOrigins);
        verify(reg).withSockJS();
    }

    @Test
    void configureMessageBroker() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void configureClientInboundChannel() {
        ChannelRegistration registration = mock(ChannelRegistration.class);

        config.configureClientInboundChannel(registration);

        verify(registration).interceptors(stompInterceptor, interceptor);
    }
}
