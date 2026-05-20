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

        String sessionId = accessor.getSessionId();

        switch (payload.getEventType()) {
            case JOIN -> handleJoin(listId, payload, sessionId);
            case LEAVE -> handleLeave(listId, payload, sessionId);
            case TYPING -> handleTyping(listId, payload);
            default -> { /* intentionally ignored */ }
        }
    }

    private void handleJoin(String listId, PresenceEvent payload, String sessionId) {
        presenceStateService.getRoomRosters()
                .computeIfAbsent(listId, k -> ConcurrentHashMap.newKeySet())
                .add(payload.getUsername());
        if (payload.getDisplayName() != null) {
            presenceStateService.getRoomDisplayNames()
                    .computeIfAbsent(listId, k -> new ConcurrentHashMap<>())
                    .put(payload.getUsername(), payload.getDisplayName());
        }
        if (sessionId != null) {
            presenceStateService.getSessionTracker().put(sessionId, payload);
        }
        broadcastRoster(listId);
    }

    private void handleLeave(String listId, PresenceEvent payload, String sessionId) {
        Map<String, String> names = presenceStateService.getRoomDisplayNames().get(listId);
        if (names != null) {
            names.remove(payload.getUsername());
        }
        Set<String> roster = presenceStateService.getRoomRosters().get(listId);
        if (roster != null) {
            roster.remove(payload.getUsername());
            broadcastRoster(listId);
        }
        if (sessionId != null) {
            presenceStateService.getSessionTracker().remove(sessionId);
        }
    }

    private void handleTyping(String listId, PresenceEvent payload) {
        if (payload.getDisplayName() != null) {
            presenceStateService.getRoomDisplayNames()
                    .computeIfAbsent(listId, k -> new ConcurrentHashMap<>())
                    .put(payload.getUsername(), payload.getDisplayName());
        }
        messagingTemplate.convertAndSend("/topic/list/" + listId + "/presence", payload);
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
