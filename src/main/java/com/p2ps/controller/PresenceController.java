package com.p2ps.controller;

import com.p2ps.dto.PresenceEvent;
import com.p2ps.service.PresenceStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller responsible for routing low-latency presence events.
 * Uses the shared PresenceStateService as the ultimate source of truth.
 */
@Controller
public class PresenceController {

    private static final Logger logger = LoggerFactory.getLogger(PresenceController.class);

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

        // Skip processing if listId is null
        if (listId == null) return;

        // Fetch the global state maps instead of using local isolated variables
        ConcurrentHashMap<String, Set<String>> roomRosters = presenceStateService.getRoomRosters();
        ConcurrentHashMap<String, PresenceEvent> sessionTracker = presenceStateService.getSessionTracker();
        String sessionId = accessor.getSessionId();

        if (payload.getEventType() == PresenceEvent.EventType.JOIN) {
            // 1. Add user to the master roster
            roomRosters.computeIfAbsent(listId, k -> ConcurrentHashMap.newKeySet()).add(payload.getUsername());
            
            // 2. Track their exact session for ghost disconnect handling
            if (sessionId != null) {
                sessionTracker.put(sessionId, payload);
            }
            
            broadcastRoster(listId);
        } 
        else if (payload.getEventType() == PresenceEvent.EventType.LEAVE) {
            // 1. Remove user from the master roster
            Set<String> roster = roomRosters.get(listId);
            if (roster != null) {
                roster.remove(payload.getUsername());
                broadcastRoster(listId);
            }
            
            // 2. Remove from session tracker
            if (sessionId != null) {
                sessionTracker.remove(sessionId);
            }
        } 
        else if (payload.getEventType() == PresenceEvent.EventType.TYPING) {
            // Pass TYPING events through normally
            messagingTemplate.convertAndSend("/topic/list/" + listId + "/presence", payload);
        }
    }

    private void broadcastRoster(String listId) {
        Set<String> roster = presenceStateService.getRoomRosters().getOrDefault(listId, ConcurrentHashMap.newKeySet());
        PresenceEvent rosterUpdate = new PresenceEvent();
        rosterUpdate.setEventType(PresenceEvent.EventType.ROSTER_UPDATE);
        rosterUpdate.setListId(listId);
        rosterUpdate.setActiveUsers(roster);
        messagingTemplate.convertAndSend("/topic/list/" + listId + "/presence", rosterUpdate);
    }
}