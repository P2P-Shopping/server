package com.p2ps.service;

import com.p2ps.dto.PresenceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceStateServiceTest {

    private PresenceStateService presenceStateService;

    @BeforeEach
    void setUp() {
        presenceStateService = new PresenceStateService();
    }

    @Test
    void getRoomRosters_shouldReturnConcurrentHashMap() {
        ConcurrentHashMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        assertNotNull(roomRosters);
        assertTrue(roomRosters instanceof ConcurrentHashMap);
    }

    @Test
    void getSessionTracker_shouldReturnConcurrentHashMap() {
        ConcurrentHashMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        assertNotNull(sessionTracker);
        assertTrue(sessionTracker instanceof ConcurrentHashMap);
    }

    @Test
    void getRoomRosters_shouldReturnSameInstance() {
        ConcurrentHashMap<String, Set<String>> firstCall = presenceStateService.getRoomRosters();
        ConcurrentHashMap<String, Set<String>> secondCall = presenceStateService.getRoomRosters();
        assertSame(firstCall, secondCall);
    }

    @Test
    void getSessionTracker_shouldReturnSameInstance() {
        ConcurrentHashMap<String, PresenceEvent> firstCall = presenceStateService.getSessionTracker();
        ConcurrentHashMap<String, PresenceEvent> secondCall = presenceStateService.getSessionTracker();
        assertSame(firstCall, secondCall);
    }

    @Test
    void roomRosters_shouldAllowAddingMembers() {
        ConcurrentHashMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user1");
        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user2");

        assertEquals(1, roomRosters.size());
        assertTrue(roomRosters.get("list-1").contains("user1"));
        assertTrue(roomRosters.get("list-1").contains("user2"));
    }

    @Test
    void roomRosters_shouldAllowRemovingMembers() {
        ConcurrentHashMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user1");
        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user2");

        roomRosters.get("list-1").remove("user1");

        assertEquals(1, roomRosters.get("list-1").size());
        assertTrue(roomRosters.get("list-1").contains("user2"));
    }

    @Test
    void sessionTracker_shouldAllowAddingSessions() {
        ConcurrentHashMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        PresenceEvent event = new PresenceEvent();
        event.setEventType(PresenceEvent.EventType.JOIN);
        event.setUsername("user1");
        event.setListId("list-1");

        sessionTracker.put("session-1", event);

        assertEquals(1, sessionTracker.size());
        assertEquals(event, sessionTracker.get("session-1"));
    }

    @Test
    void sessionTracker_shouldAllowRemovingSessions() {
        ConcurrentHashMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        PresenceEvent event = new PresenceEvent();
        event.setEventType(PresenceEvent.EventType.JOIN);
        event.setUsername("user1");

        sessionTracker.put("session-1", event);
        sessionTracker.remove("session-1");

        assertTrue(sessionTracker.isEmpty());
    }

    @Test
    void roomRosters_and_sessionTracker_shouldBeIndependent() {
        ConcurrentHashMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        ConcurrentHashMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();

        roomRosters.put("list-1", ConcurrentHashMap.newKeySet());
        sessionTracker.put("session-1", new PresenceEvent());

        assertEquals(1, roomRosters.size());
        assertEquals(1, sessionTracker.size());
    }

    @Test
    void shouldHandleMultipleLists() {
        ConcurrentHashMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();

        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user1");
        roomRosters.computeIfAbsent("list-2", k -> ConcurrentHashMap.newKeySet()).add("user2");

        assertEquals(2, roomRosters.size());
        assertTrue(roomRosters.get("list-1").contains("user1"));
        assertTrue(roomRosters.get("list-2").contains("user2"));
    }

    @Test
    void getRoomRosters_shouldReturnEmptyMapInitially() {
        ConcurrentHashMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        assertTrue(roomRosters.isEmpty());
    }

    @Test
    void getSessionTracker_shouldReturnEmptyMapInitially() {
        ConcurrentHashMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        assertTrue(sessionTracker.isEmpty());
    }

    @Test
    void operations_shouldNotThrowExceptions() {
        assertDoesNotThrow(() -> {
            presenceStateService.getRoomRosters().put("key", ConcurrentHashMap.newKeySet());
            presenceStateService.getSessionTracker().put("key", new PresenceEvent());
            presenceStateService.getRoomRosters().clear();
            presenceStateService.getSessionTracker().clear();
        });
    }
}
