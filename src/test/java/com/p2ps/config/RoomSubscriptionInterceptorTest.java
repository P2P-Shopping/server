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

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
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

    @ParameterizedTest(name = "destination={0}")
    @MethodSource("rejectedSubscriptionDestinations")
    void preSend_RejectedSubscriptions_ReturnNull(String destination) {
        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                destination,
                new UsernamePasswordAuthenticationToken("test@test.com", null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    static Stream<String> rejectedSubscriptionDestinations() {
        return Stream.of(
                "/topic/list/invalid_ID!",
                "/topic/list/not-a-uuid"
        );
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

    @Test
    void getAuthenticatedUser_withSessionAttributes_reauthenticates() {
        String token = "valid-token";
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user@test.com", null);
        
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new java.util.HashMap<>(java.util.Map.of(
            JwtHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE, token
        )));
        
        UUID listId = UUID.randomUUID();
        accessor.setDestination("/topic/list/" + listId);
        
        when(jwtAuthFilter.authenticateToken(token)).thenReturn(auth);
        when(shoppingListRepository.existsByIdAndUserEmailOrCollaboratorEmail(listId, "user@test.com")).thenReturn(true);
        
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        
        Message<?> result = interceptor.preSend(message, channel);
        
        assertThat(result).isNotNull();
        verify(jwtAuthFilter).authenticateToken(token);
    }

    @Test
    void preSend_ValidInvitationSubscription_OwnEmail() {
        String userEmail = "user@test.com";
        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/invitations/" + userEmail,
                new UsernamePasswordAuthenticationToken(userEmail, null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
    }

    @Test
    void preSend_InvitationSubscription_EmptyEmail_ReturnsNull() {
        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/invitations/",
                new UsernamePasswordAuthenticationToken("user@test.com", null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    @Test
    void preSend_InvitationSubscription_WrongUser_ReturnsNull() {
        Message<?> message = createMessage(
                StompCommand.SUBSCRIBE,
                "/topic/invitations/other@test.com",
                new UsernamePasswordAuthenticationToken("user@test.com", null, java.util.List.of())
        );
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }

    @Test
    void preSend_NonAuthenticationPrincipal_FallsBackToSessionAttributes() {
        Principal nonAuthPrincipal = new Principal() {
            @Override
            public String getName() {
                return "user@test.com";
            }
        };

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setUser(nonAuthPrincipal);
        accessor.setSessionAttributes(new HashMap<>(Map.of(
            JwtHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE, "some-token"
        )));

        UUID listId = UUID.randomUUID();
        accessor.setDestination("/topic/list/" + listId);

        when(jwtAuthFilter.authenticateToken("some-token")).thenReturn(
                new UsernamePasswordAuthenticationToken("user@test.com", null, java.util.List.of()));
        when(shoppingListRepository.existsByIdAndUserEmailOrCollaboratorEmail(listId, "user@test.com")).thenReturn(true);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        verify(jwtAuthFilter).authenticateToken("some-token");
    }

    @Test
    void preSend_NoSessionAttributes_NoToken_ReturnsNull() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(new HashMap<>());

        UUID listId = UUID.randomUUID();
        accessor.setDestination("/topic/list/" + listId);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = interceptor.preSend(message, channel);

        assertNull(result);
    }
}
