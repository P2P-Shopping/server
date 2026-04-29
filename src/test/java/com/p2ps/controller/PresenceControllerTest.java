package com.p2ps.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.p2ps.dto.PresenceEvent;
import com.p2ps.service.PresenceStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class PresenceControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PresenceStateService presenceStateService;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    @InjectMocks
    private PresenceController presenceController;

    private PresenceEvent samplePayload;
    private ConcurrentHashMap<String, Set<String>> roomRosters;
    private ConcurrentHashMap<String, PresenceEvent> sessionTracker;

    @BeforeEach
    void setUp() {
        samplePayload = new PresenceEvent();
        samplePayload.setEventType(PresenceEvent.EventType.JOIN);
        samplePayload.setUsername("testUser");
        samplePayload.setListId("1234-abcd");

        roomRosters = new ConcurrentHashMap<>();
        sessionTracker = new ConcurrentHashMap<>();

        lenient().when(presenceStateService.getRoomRosters()).thenReturn(roomRosters);
        lenient().when(presenceStateService.getSessionTracker()).thenReturn(sessionTracker);
    }

    @Test
    void handlePresenceEvent_ShouldRouteCorrectlyWithoutDatabase() {
        String testListId = "1234-abcd";
        samplePayload.setListId("mismatched-id");

        presenceController.handlePresenceEvent(testListId, samplePayload, headerAccessor);

        assertEquals(testListId, samplePayload.getListId());
        
        ArgumentCaptor<PresenceEvent> eventCaptor = ArgumentCaptor.forClass(PresenceEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/list/" + testListId + "/presence"), eventCaptor.capture());
        PresenceEvent sentEvent = eventCaptor.getValue();
        assertEquals(PresenceEvent.EventType.ROSTER_UPDATE, sentEvent.getEventType());
        assertEquals(testListId, sentEvent.getListId());
        assertTrue(sentEvent.getActiveUsers().contains("testUser"));
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void handlePresenceEvent_WithNullEventType_ShouldRouteCorrectlyWithoutDatabase() {
        String testListId = "1234-abcd";
        samplePayload.setEventType(null);

        presenceController.handlePresenceEvent(testListId, samplePayload, headerAccessor);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handlePresenceEvent_WithNullListId_ShouldHandleGracefully() {
        presenceController.handlePresenceEvent(null, samplePayload, headerAccessor);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handlePresenceEvent_WithNullPayload_ShouldNotSendMessage() {
        presenceController.handlePresenceEvent("1234-abcd", null, headerAccessor);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handlePresenceEvent_WithDebugLoggingEnabled_ShouldStillRouteCorrectly() {
        Logger controllerLogger = (Logger) LoggerFactory.getLogger(PresenceController.class);
        Level originalLevel = controllerLogger.getLevel();

        try {
            controllerLogger.setLevel(Level.DEBUG);

            String testListId = "debug-list";
            samplePayload.setListId("mismatched-id");

            presenceController.handlePresenceEvent(testListId, samplePayload, headerAccessor);

            assertEquals(testListId, samplePayload.getListId());
            
            ArgumentCaptor<PresenceEvent> eventCaptor = ArgumentCaptor.forClass(PresenceEvent.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/list/" + testListId + "/presence"), eventCaptor.capture());
            PresenceEvent sentEvent = eventCaptor.getValue();
            assertEquals(PresenceEvent.EventType.ROSTER_UPDATE, sentEvent.getEventType());
            assertEquals(testListId, sentEvent.getListId());
            assertTrue(sentEvent.getActiveUsers().contains("testUser"));
            verifyNoMoreInteractions(messagingTemplate);
        } finally {
            controllerLogger.setLevel(originalLevel);
        }
    }
}
