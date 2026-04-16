package com.p2ps.controller;

import com.p2ps.dto.PresenceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.verify;
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

        presenceController.handlePresenceEvent(testListId, samplePayload);

        verify(messagingTemplate).convertAndSend("/topic/list/" + testListId + "/presence", samplePayload);
        verifyNoMoreInteractions(messagingTemplate);
    }
}
