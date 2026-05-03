package com.p2ps.config;

import com.p2ps.lists.repo.ShoppingListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomSubscriptionInterceptorTest {

    private ShoppingListRepository shoppingListRepository;
    private com.p2ps.auth.security.JwtAuthFilter jwtAuthFilter;
    private RoomSubscriptionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        shoppingListRepository = mock(ShoppingListRepository.class);
        jwtAuthFilter = mock(com.p2ps.auth.security.JwtAuthFilter.class);
        interceptor = new RoomSubscriptionInterceptor(shoppingListRepository, jwtAuthFilter);
    }

    private Message<?> createMessage(StompCommand command, String destination) {
        return createMessage(command, destination, null);
    }

    private Message<?> createMessage(StompCommand command, String destination, UsernamePasswordAuthenticationToken user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_ValidSubscription_Owner() {
        UUID listId = UUID.randomUUID();
        String userEmail = "test@test.com";

        when(shoppingListRepository.existsByIdAndUserEmailOrCollaboratorEmail(listId, userEmail)).thenReturn(true);

        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/list/" + listId,
                new UsernamePasswordAuthenticationToken(userEmail, null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void preSend_ValidSubscription_Collaborator() {
        UUID listId = UUID.randomUUID();
        String userEmail = "collab@test.com";

        when(shoppingListRepository.existsByIdAndUserEmailOrCollaboratorEmail(listId, userEmail)).thenReturn(true);

        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/list/" + listId,
                new UsernamePasswordAuthenticationToken(userEmail, null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void preSend_UnauthorizedSubscription_ReturnsNull() {
        UUID listId = UUID.randomUUID();
        String userEmail = "hacker@test.com";

        when(shoppingListRepository.existsByIdAndUserEmailOrCollaboratorEmail(listId, userEmail)).thenReturn(false);

        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/list/" + listId,
                new UsernamePasswordAuthenticationToken(userEmail, null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    @Test
    void preSend_ValidSubscription_Presence() {
        UUID listId = UUID.randomUUID();
        String userEmail = "test@test.com";

        when(shoppingListRepository.existsByIdAndUserEmailOrCollaboratorEmail(listId, userEmail)).thenReturn(true);

        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/list/" + listId + "/presence",
                new UsernamePasswordAuthenticationToken(userEmail, null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void preSend_InvalidSubscriptionId_ReturnsNull() {
        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/list/invalid_ID!",
                new UsernamePasswordAuthenticationToken("test@test.com", null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    @Test
    void preSend_SubscriptionToNonExistentList_ReturnsNull() {
        UUID listId = UUID.randomUUID();
        String userEmail = "test@test.com";
        when(shoppingListRepository.existsByIdAndUserEmailOrCollaboratorEmail(listId, userEmail)).thenReturn(false);

        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/list/" + listId,
                new UsernamePasswordAuthenticationToken(userEmail, null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    @ParameterizedTest(name = "command={0}, destination={1}")
    @MethodSource("nonBlockingSubscriptions")
    void preSend_NonBlockingSubscriptions_Pass(StompCommand command, String destination) {
        Message<?> message = command == StompCommand.SUBSCRIBE
                ? createMessage(command, destination, new UsernamePasswordAuthenticationToken("test@test.com", null, java.util.List.of()))
                : createMessage(command, destination);
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

    @Test
    void preSend_SubscribeWithoutPrincipal_ReturnsNull() {
        Message<?> message = createMessage(StompCommand.SUBSCRIBE, "/topic/list/" + UUID.randomUUID());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    @Test
    void preSend_NullDestination_ReturnsMessage() {
        Message<?> message = createMessage(StompCommand.SUBSCRIBE, null);
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void preSend_NonListTopic_PassesThrough() {
        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/other/topic",
                new UsernamePasswordAuthenticationToken("test@test.com", null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void preSend_InvalidUuidFormat_ReturnsNull() {
        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/list/not-a-uuid",
                new UsernamePasswordAuthenticationToken("test@test.com", null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    @Test
    void extractListId_withPresenceSuffix_removesSuffix() throws Exception {
        RoomSubscriptionInterceptor testInterceptor = new RoomSubscriptionInterceptor(mock(ShoppingListRepository.class), mock(com.p2ps.auth.security.JwtAuthFilter.class));
        java.lang.reflect.Method method = testInterceptor.getClass().getDeclaredMethod("extractListId", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(testInterceptor, "/topic/list/123/presence");

        assertThat(result).isEqualTo("123");
    }

    @Test
    void extractListId_withoutPresenceSuffix_returnsFullId() throws Exception {
        RoomSubscriptionInterceptor testInterceptor = new RoomSubscriptionInterceptor(mock(ShoppingListRepository.class), mock(com.p2ps.auth.security.JwtAuthFilter.class));
        java.lang.reflect.Method method = testInterceptor.getClass().getDeclaredMethod("extractListId", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(testInterceptor, "/topic/list/123");

        assertThat(result).isEqualTo("123");
    }
}
