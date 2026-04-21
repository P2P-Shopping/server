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
        /**
         * Represents a user joining a room.
         */
        JOIN,
        /**
         * Represents a user leaving a room.
         */
        LEAVE,
        /**
         * Represents a user actively typing in a room.
         */
        TYPING
    }

    /**
     * The username of the user triggering the event.
     */
    private String username;

    /**
     * The type of the event triggered.
     */
    private EventType eventType;

    /**
     * The unique identifier of the list/room.
     */
    private String listId;

    /**
     * Default no-args constructor required for Jackson deserialization.
     */
    public PresenceEvent() {
        // Required for Jackson deserialization by Spring internally
    }

    /**
     * Gets the username of the user.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username of the user.
     *
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the type of the event.
     *
     * @return the event type
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Sets the type of the event.
     *
     * @param eventType the event type to set
     */
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    /**
     * Gets the unique identifier of the list.
     *
     * @return the list ID
     */
    public String getListId() {
        return listId;
    }

    /**
     * Sets the unique identifier of the list.
     *
     * @param listId the list ID to set
     */
    public void setListId(String listId) {
        this.listId = listId;
    }
}
