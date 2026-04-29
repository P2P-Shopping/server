package com.p2ps.service;

import com.p2ps.dto.PresenceEvent;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceStateService {

    private final ConcurrentHashMap<String, Set<String>> roomRosters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PresenceEvent> sessionTracker = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, Set<String>> getRoomRosters() {
        return roomRosters;
    }

    public ConcurrentHashMap<String, PresenceEvent> getSessionTracker() {
        return sessionTracker;
    }
}