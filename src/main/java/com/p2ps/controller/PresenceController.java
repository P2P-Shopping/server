package com.p2ps.controller;

import com.p2ps.dto.PresenceEvent;
import com.p2ps.service.PresenceStateService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Controller
public class PresenceController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceStateService presenceStateService;

    public PresenceController(SimpMessagingTemplate messagingTemplate, PresenceStateService presenceStateService) {
        this.messagingTemplate = messagingTemplate;
        this.presenceStateService = presenceStateService;
    }

    @MessageMapping("/list/{listId}/presence")
    public void handlePresenceEvent(@DestinationVariable String listId, PresenceEvent payload, SimpMessageHeaderAccessor accessor) {
        if (payload == null || payload.getEventType() == null) return;
        payload.setListId(listId);
        if (listId == null) return;

        ConcurrentMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        ConcurrentMap<String, Map<String, String>> roomDisplayNames = presenceStateService.getRoomDisplayNames();
        ConcurrentMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        String sessionId = accessor.getSessionId();

        if (payload.getEventType() == PresenceEvent.EventType.JOIN) {
            roomRosters.computeIfAbsent(listId, k -> ConcurrentHashMap.newKeySet()).add(payload.getUsername());
            if (payload.getDisplayName() != null) {
                roomDisplayNames.computeIfAbsent(listId, k -> new ConcurrentHashMap<>()).put(payload.getUsername(), payload.getDisplayName());
            }
            if (sessionId != null) {
                sessionTracker.put(sessionId, payload);
            }
            broadcastRoster(listId);
        } 
        else if (payload.getEventType() == PresenceEvent.EventType.LEAVE) {
            Set<String> roster = roomRosters.get(listId);
            if (roster != null) {
                roster.remove(payload.getUsername());
                broadcastRoster(listId);
            }
            Map<String, String> names = roomDisplayNames.get(listId);
            if (names != null) {
                names.remove(payload.getUsername());
            }
            if (sessionId != null) {
                sessionTracker.remove(sessionId);
            }
        } 
        else if (payload.getEventType() == PresenceEvent.EventType.TYPING) {
            if (payload.getDisplayName() != null) {
                roomDisplayNames.computeIfAbsent(listId, k -> new ConcurrentHashMap<>()).put(payload.getUsername(), payload.getDisplayName());
            }
            messagingTemplate.convertAndSend("/topic/list/" + listId + "/presence", payload);
        }
    }

    private void broadcastRoster(String listId) {
        Set<String> roster = presenceStateService.getRoomRosters().getOrDefault(listId, ConcurrentHashMap.newKeySet());
        Map<String, String> names = presenceStateService.getRoomDisplayNames().getOrDefault(listId, new ConcurrentHashMap<>());
        PresenceEvent rosterUpdate = new PresenceEvent();
        rosterUpdate.setEventType(PresenceEvent.EventType.ROSTER_UPDATE);
        rosterUpdate.setListId(listId);
        rosterUpdate.setActiveUsers(roster);
        rosterUpdate.setDisplayNames(new java.util.HashMap<>(names));
        messagingTemplate.convertAndSend("/topic/list/" + listId + "/presence", rosterUpdate);
    }
}
