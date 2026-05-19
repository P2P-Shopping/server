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
    void testDisplayNameGetterAndSetter() {
        PresenceEvent event = new PresenceEvent();
        assertNull(event.getDisplayName());
        event.setDisplayName("Test Name");
        assertEquals("Test Name", event.getDisplayName());
    }

    @Test
    void testActiveUsersGetterAndSetter() {
        PresenceEvent event = new PresenceEvent();
        assertNull(event.getActiveUsers());
        java.util.Set<String> users = new java.util.HashSet<>();
        users.add("user1");
        users.add("user2");
        event.setActiveUsers(users);
        assertEquals(2, event.getActiveUsers().size());
        assertTrue(event.getActiveUsers().contains("user1"));
        assertTrue(event.getActiveUsers().contains("user2"));
    }

    @Test
    void testDisplayNamesGetterAndSetter() {
        PresenceEvent event = new PresenceEvent();
        assertNull(event.getDisplayNames());
        java.util.Map<String, String> names = new java.util.HashMap<>();
        names.put("user1", "User One");
        names.put("user2", "User Two");
        event.setDisplayNames(names);
        assertEquals(2, event.getDisplayNames().size());
        assertEquals("User One", event.getDisplayNames().get("user1"));
        assertEquals("User Two", event.getDisplayNames().get("user2"));
    }

    @Test
    void testEnumValues() {
        assertEquals(5, PresenceEvent.EventType.values().length);
        assertEquals(PresenceEvent.EventType.JOIN, PresenceEvent.EventType.valueOf("JOIN"));
        assertEquals(PresenceEvent.EventType.LEAVE, PresenceEvent.EventType.valueOf("LEAVE"));
        assertEquals(PresenceEvent.EventType.TYPING, PresenceEvent.EventType.valueOf("TYPING"));
        assertEquals(PresenceEvent.EventType.SYNC, PresenceEvent.EventType.valueOf("SYNC"));
        assertEquals(PresenceEvent.EventType.ROSTER_UPDATE, PresenceEvent.EventType.valueOf("ROSTER_UPDATE"));
    }
}
