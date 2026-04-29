package com.p2ps.service;

import com.p2ps.dto.PresenceEvent;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PresenceStateService {

    private final ConcurrentMap<String, Set<String>> roomRosters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PresenceEvent> sessionTracker = new ConcurrentHashMap<>();

    public ConcurrentMap<String, Set<String>> getRoomRosters() {
        return roomRosters;
    }

    public ConcurrentMap<String, PresenceEvent> getSessionTracker() {
        return sessionTracker;
    }
}