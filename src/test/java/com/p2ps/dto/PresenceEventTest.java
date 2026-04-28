package com.p2ps.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresenceEventTest {

    @Test
    void testGettersAndSetters() {
        PresenceEvent event = new PresenceEvent();
        
        event.setUsername("testUser");
        assertEquals("testUser", event.getUsername());
        
        event.setEventType(PresenceEvent.EventType.TYPING);
        assertEquals(PresenceEvent.EventType.TYPING, event.getEventType());
        
        event.setListId("list-123");
        assertEquals("list-123", event.getListId());
    }

    @Test
    void testEnumValues() {
        assertEquals(4, PresenceEvent.EventType.values().length);
        assertEquals(PresenceEvent.EventType.JOIN, PresenceEvent.EventType.valueOf("JOIN"));
        assertEquals(PresenceEvent.EventType.LEAVE, PresenceEvent.EventType.valueOf("LEAVE"));
        assertEquals(PresenceEvent.EventType.TYPING, PresenceEvent.EventType.valueOf("TYPING"));
    }
}
