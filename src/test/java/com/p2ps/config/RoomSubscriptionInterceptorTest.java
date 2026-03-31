package com.p2ps.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RoomSubscriptionInterceptorTest {

    private final RoomSubscriptionInterceptor interceptor = new RoomSubscriptionInterceptor();

    private Message<?> createMessage(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_ValidSubscription() {
        Message<?> message = createMessage(StompCommand.SUBSCRIBE, "/topic/list/valid-ID-123");
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void preSend_InvalidSubscription_ReturnsNull() {
        Message<?> message = createMessage(StompCommand.SUBSCRIBE, "/topic/list/invalid_ID!");
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    @ParameterizedTest(name = "command={0}, destination={1}")
    @MethodSource("nonBlockingSubscriptions")
    void preSend_NonBlockingSubscriptions_Pass(StompCommand command, String destination) {
        Message<?> message = createMessage(command, destination);
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    static Stream<Arguments> nonBlockingSubscriptions() {
        return Stream.of(
                Arguments.of(StompCommand.SEND, "/topic/list/invalid_ID!"),
                Arguments.of(StompCommand.SUBSCRIBE, null),
                Arguments.of(StompCommand.SUBSCRIBE, "/topic/other/invalid_ID!")
        );
    }
}

