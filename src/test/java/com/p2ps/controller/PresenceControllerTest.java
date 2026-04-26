package com.p2ps.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.p2ps.dto.PresenceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PresenceControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PresenceController presenceController;

    private PresenceEvent samplePayload;

    @BeforeEach
    void setUp() {
        samplePayload = new PresenceEvent();
        samplePayload.setEventType(PresenceEvent.EventType.JOIN);
        samplePayload.setUsername("testUser");
        samplePayload.setListId("1234-abcd");
    }

    @Test
    void handlePresenceEvent_ShouldRouteCorrectlyWithoutDatabase() {
        String testListId = "1234-abcd";
        samplePayload.setListId("mismatched-id");

        presenceController.handlePresenceEvent(testListId, samplePayload);

        assertEquals(testListId, samplePayload.getListId());
        verify(messagingTemplate).convertAndSend("/topic/list/" + testListId + "/presence", samplePayload);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void handlePresenceEvent_WithNullEventType_ShouldRouteCorrectlyWithoutDatabase() {
        String testListId = "1234-abcd";
        samplePayload.setEventType(null);

        presenceController.handlePresenceEvent(testListId, samplePayload);

        verify(messagingTemplate).convertAndSend("/topic/list/" + testListId + "/presence", samplePayload);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void handlePresenceEvent_WithNullListId_ShouldHandleGracefully() {
        presenceController.handlePresenceEvent(null, samplePayload);

        verify(messagingTemplate).convertAndSend("/topic/list/null/presence", samplePayload);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void handlePresenceEvent_WithNullPayload_ShouldNotSendMessage() {
        presenceController.handlePresenceEvent("1234-abcd", null);

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

            presenceController.handlePresenceEvent(testListId, samplePayload);

            assertEquals(testListId, samplePayload.getListId());
            verify(messagingTemplate).convertAndSend("/topic/list/" + testListId + "/presence", samplePayload);
            verifyNoMoreInteractions(messagingTemplate);
        } finally {
            controllerLogger.setLevel(originalLevel);
        }
    }
}
