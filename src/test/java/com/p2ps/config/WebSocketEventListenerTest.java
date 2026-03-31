package com.p2ps.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class WebSocketEventListenerTest {

    @Test
    void shouldHandleConnectEventWithoutThrowing() {
        WebSocketEventListener listener = new WebSocketEventListener();
        SessionConnectedEvent event = new SessionConnectedEvent(this, MessageBuilder.withPayload(new byte[0]).build());

        assertDoesNotThrow(() -> listener.handleWebSocketConnectListener(event));
    }

    @Test
    void shouldHandleDisconnectEventWhenSessionIdIsNull() {
        WebSocketEventListener listener = new WebSocketEventListener();
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-1", CloseStatus.NORMAL);

        assertDoesNotThrow(() -> listener.handleWebSocketDisconnectListener(event));
    }

    @Test
    void shouldHandleDisconnectEventWhenSessionIdIsShort() {
        WebSocketEventListener listener = new WebSocketEventListener();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("abc");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "abc", CloseStatus.NORMAL);

        assertDoesNotThrow(() -> listener.handleWebSocketDisconnectListener(event));
    }
}
