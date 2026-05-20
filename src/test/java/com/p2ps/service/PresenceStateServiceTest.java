package com.p2ps.service;

import com.p2ps.dto.PresenceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    void getRoomRosters_shouldReturnConcurrentMap() {
        ConcurrentMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        assertNotNull(roomRosters);
    }

    @Test
    void getSessionTracker_shouldReturnConcurrentMap() {
        ConcurrentMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        assertNotNull(sessionTracker);
    }

    @Test
    void getRoomRosters_shouldReturnSameInstance() {
        ConcurrentMap<String, Set<String>> firstCall = presenceStateService.getRoomRosters();
        ConcurrentMap<String, Set<String>> secondCall = presenceStateService.getRoomRosters();
        assertSame(firstCall, secondCall);
    }

    @Test
    void getSessionTracker_shouldReturnSameInstance() {
        ConcurrentMap<String, PresenceEvent> firstCall = presenceStateService.getSessionTracker();
        ConcurrentMap<String, PresenceEvent> secondCall = presenceStateService.getSessionTracker();
        assertSame(firstCall, secondCall);
    }

    @Test
    void roomRosters_shouldAllowAddingMembers() {
        ConcurrentMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user1");
        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user2");

        assertEquals(1, roomRosters.size());
        assertTrue(roomRosters.get("list-1").contains("user1"));
        assertTrue(roomRosters.get("list-1").contains("user2"));
    }

    @Test
    void roomRosters_shouldAllowRemovingMembers() {
        ConcurrentMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user1");
        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user2");

        roomRosters.get("list-1").remove("user1");

        assertEquals(1, roomRosters.get("list-1").size());
        assertTrue(roomRosters.get("list-1").contains("user2"));
    }

    @Test
    void sessionTracker_shouldAllowAddingSessions() {
        ConcurrentMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
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
        ConcurrentMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        PresenceEvent event = new PresenceEvent();
        event.setEventType(PresenceEvent.EventType.JOIN);
        event.setUsername("user1");

        sessionTracker.put("session-1", event);
        sessionTracker.remove("session-1");

        assertTrue(sessionTracker.isEmpty());
    }

    @Test
    void roomRosters_and_sessionTracker_shouldBeIndependent() {
        ConcurrentMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        ConcurrentMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();

        roomRosters.put("list-1", ConcurrentHashMap.newKeySet());
        sessionTracker.put("session-1", new PresenceEvent());

        assertEquals(1, roomRosters.size());
        assertEquals(1, sessionTracker.size());
    }

    @Test
    void shouldHandleMultipleLists() {
        ConcurrentMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();

        roomRosters.computeIfAbsent("list-1", k -> ConcurrentHashMap.newKeySet()).add("user1");
        roomRosters.computeIfAbsent("list-2", k -> ConcurrentHashMap.newKeySet()).add("user2");

        assertEquals(2, roomRosters.size());
        assertTrue(roomRosters.get("list-1").contains("user1"));
        assertTrue(roomRosters.get("list-2").contains("user2"));
    }

    @Test
    void getRoomRosters_shouldReturnEmptyMapInitially() {
        ConcurrentMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        assertTrue(roomRosters.isEmpty());
    }

    @Test
    void getSessionTracker_shouldReturnEmptyMapInitially() {
        ConcurrentMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        assertTrue(sessionTracker.isEmpty());
    }

    @Test
    void getRoomDisplayNames_shouldReturnConcurrentMap() {
        ConcurrentMap<String, java.util.Map<String, String>> displayNames = presenceStateService.getRoomDisplayNames();
        assertNotNull(displayNames);
    }

    @Test
    void getRoomDisplayNames_shouldReturnSameInstance() {
        ConcurrentMap<String, java.util.Map<String, String>> first = presenceStateService.getRoomDisplayNames();
        ConcurrentMap<String, java.util.Map<String, String>> second = presenceStateService.getRoomDisplayNames();
        assertSame(first, second);
    }

    @Test
    void getRoomDisplayNames_shouldReturnEmptyMapInitially() {
        assertTrue(presenceStateService.getRoomDisplayNames().isEmpty());
    }

    @Test
    void operations_shouldNotThrowExceptions() {
        assertDoesNotThrow(() -> {
            presenceStateService.getRoomRosters().put("key", ConcurrentHashMap.newKeySet());
            presenceStateService.getRoomDisplayNames().put("key", new ConcurrentHashMap<>());
            presenceStateService.getSessionTracker().put("key", new PresenceEvent());
            presenceStateService.getRoomRosters().clear();
            presenceStateService.getRoomDisplayNames().clear();
            presenceStateService.getSessionTracker().clear();
        });
    }
}
