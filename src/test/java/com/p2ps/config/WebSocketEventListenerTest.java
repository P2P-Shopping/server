package com.p2ps.config;

import com.p2ps.dto.PresenceEvent;
import com.p2ps.service.PresenceStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private PresenceStateService presenceStateService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketEventListener listener;

    private ConcurrentHashMap<String, PresenceEvent> sessionTracker;
    private ConcurrentHashMap<String, Set<String>> roomRosters;
    private ConcurrentHashMap<String, java.util.Map<String, String>> roomDisplayNames;

    @BeforeEach
    void setUp() {
        sessionTracker = new ConcurrentHashMap<>();
        roomRosters = new ConcurrentHashMap<>();
        roomDisplayNames = new ConcurrentHashMap<>();

        listener = new WebSocketEventListener(presenceStateService, messagingTemplate);
        lenient().when(presenceStateService.getSessionTracker()).thenReturn(sessionTracker);
        lenient().when(presenceStateService.getRoomRosters()).thenReturn(roomRosters);
        lenient().when(presenceStateService.getRoomDisplayNames()).thenReturn(roomDisplayNames);
    }

    @Test
    void shouldHandleConnectEventWithoutThrowing() {
        SessionConnectedEvent event = new SessionConnectedEvent(this, MessageBuilder.withPayload(new byte[0]).build());

        assertDoesNotThrow(() -> listener.handleWebSocketConnectListener(event));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"abc", "12345678", "very-long-session-id-12345"})
    void shouldHandleDisconnectWithVariousSessionIds(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, sessionId != null ? sessionId : "unknown", CloseStatus.NORMAL);

        assertDoesNotThrow(() -> listener.handleWebSocketDisconnectListener(event));
    }

    @Test
    void shouldRemoveSessionFromTrackerOnDisconnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        PresenceEvent sessionEvent = new PresenceEvent();
        sessionEvent.setEventType(PresenceEvent.EventType.JOIN);
        sessionEvent.setListId("list-1");
        sessionEvent.setUsername("user1");
        sessionTracker.put("session-123", sessionEvent);

        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        listener.handleWebSocketDisconnectListener(event);

        verify(presenceStateService).getSessionTracker();
        assert(!sessionTracker.containsKey("session-123"));
    }

    @Test
    void shouldRemoveUserFromRosterOnDisconnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        PresenceEvent sessionEvent = new PresenceEvent();
        sessionEvent.setEventType(PresenceEvent.EventType.JOIN);
        sessionEvent.setListId("list-1");
        sessionEvent.setUsername("user1");
        sessionTracker.put("session-123", sessionEvent);

        Set<String> roster = ConcurrentHashMap.newKeySet();
        roster.add("user1");
        roster.add("user2");
        roomRosters.put("list-1", roster);

        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        listener.handleWebSocketDisconnectListener(event);

        assertFalse(roster.contains("user1"));
        assertTrue(roster.contains("user2"));
    }

    @Test
    void shouldBroadcastRosterUpdateOnDisconnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        PresenceEvent sessionEvent = new PresenceEvent();
        sessionEvent.setEventType(PresenceEvent.EventType.JOIN);
        sessionEvent.setListId("list-1");
        sessionEvent.setUsername("user1");
        sessionTracker.put("session-123", sessionEvent);

        Set<String> roster = ConcurrentHashMap.newKeySet();
        roster.add("user1");
        roomRosters.put("list-1", roster);

        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        listener.handleWebSocketDisconnectListener(event);

        ArgumentCaptor<PresenceEvent> eventCaptor = ArgumentCaptor.forClass(PresenceEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/list/list-1/presence"), eventCaptor.capture());

        PresenceEvent sentEvent = eventCaptor.getValue();
        assert(sentEvent.getEventType() == PresenceEvent.EventType.ROSTER_UPDATE);
        assert(sentEvent.getListId().equals("list-1"));
        assert(!sentEvent.getActiveUsers().contains("user1"));
    }

    @Test
    void shouldNotBroadcastWhenRosterDoesNotExist() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        PresenceEvent sessionEvent = new PresenceEvent();
        sessionEvent.setEventType(PresenceEvent.EventType.JOIN);
        sessionEvent.setListId("list-999");
        sessionEvent.setUsername("user1");
        sessionTracker.put("session-123", sessionEvent);

        // No roster for list-999
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        listener.handleWebSocketDisconnectListener(event);

        org.mockito.Mockito.verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void shouldHandleDisconnectWhenSessionNotInTracker() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Session not in tracker
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        assertDoesNotThrow(() -> listener.handleWebSocketDisconnectListener(event));
    }

    @Test
    void shouldRemoveDisplayNameOnDisconnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        PresenceEvent sessionEvent = new PresenceEvent();
        sessionEvent.setEventType(PresenceEvent.EventType.JOIN);
        sessionEvent.setListId("list-1");
        sessionEvent.setUsername("user1");
        sessionTracker.put("session-123", sessionEvent);

        Set<String> roster = ConcurrentHashMap.newKeySet();
        roster.add("user1");
        roomRosters.put("list-1", roster);

        java.util.Map<String, String> names = new ConcurrentHashMap<>();
        names.put("user1", "User One");
        names.put("user2", "User Two");
        roomDisplayNames.put("list-1", names);

        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);

        listener.handleWebSocketDisconnectListener(event);

        assertFalse(names.containsKey("user1"));
        assertTrue(names.containsKey("user2"));

        ArgumentCaptor<PresenceEvent> eventCaptor = ArgumentCaptor.forClass(PresenceEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/list/list-1/presence"), eventCaptor.capture());

        PresenceEvent sentEvent = eventCaptor.getValue();
        assertTrue(sentEvent.getDisplayNames().containsKey("user2"));
        assertFalse(sentEvent.getDisplayNames().containsKey("user1"));
    }

}
