package com.p2ps.dto;

/**
 * Data Transfer Object for presence events in a collaborative room.
 * This class represents when a user joins, leaves, or starts typing.
 */
public class PresenceEvent {

    /**
     * The type of presence event.
     */
    public enum EventType {
        JOIN,
        LEAVE,
        TYPING,
        SYNC,
        ROSTER_UPDATE
    }

    @com.fasterxml.jackson.annotation.JsonProperty("username")
    private String username;

    @com.fasterxml.jackson.annotation.JsonProperty("displayName")
    private String displayName;

    @com.fasterxml.jackson.annotation.JsonProperty("eventType")
    private EventType eventType;

    @com.fasterxml.jackson.annotation.JsonProperty("listId")
    private String listId;

    @com.fasterxml.jackson.annotation.JsonProperty("activeUsers")
    private java.util.Set<String> activeUsers;

    @com.fasterxml.jackson.annotation.JsonProperty("displayNames")
    private java.util.Map<String, String> displayNames;

    public PresenceEvent() {
        // Required for Jackson deserialization
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }

    public java.util.Set<String> getActiveUsers() { return activeUsers; }
    public void setActiveUsers(java.util.Set<String> activeUsers) { this.activeUsers = activeUsers; }

    public java.util.Map<String, String> getDisplayNames() { return displayNames; }
    public void setDisplayNames(java.util.Map<String, String> displayNames) { this.displayNames = displayNames; }
}
