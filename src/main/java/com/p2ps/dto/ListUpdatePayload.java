package com.p2ps.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * Data Transfer Object representing a real-time modification to a shopping list.
 * Defines the standard structure for messages broadcasted to list-specific WebSocket rooms.
 */
public class ListUpdatePayload {

    public static final String STATUS_SUCCESS = "Success";
    public static final String STATUS_REJECTION = "Rejection";

    private ActionType action = ActionType.UNKNOWN;
    private String itemId;
    private String content;
    private Boolean checked;
    private Long timestamp;
    private String status;

    /**
     * Default constructor required for JSON deserialization by Jackson.
     */
    public ListUpdatePayload() {}

    /**
     * Gets the action type.
     * @return the action type
     */
    public ActionType getAction() {
        return action;
    }

    @JsonAlias({"actionType", "action_type"})
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    public void setAction(ActionType action) {
        this.action = action == null ? ActionType.UNKNOWN : action;
    }

    /**
     * Gets the unique identifier of the modified item.
     * @return the item ID
     */
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    /**
     * Gets the text content or value of the item.
     * @return the item content
     */
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getChecked() {
        return checked;
    }

    @JsonAlias({"isChecked", "completed"})
    public void setChecked(Boolean checked) {
        this.checked = checked;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status != null
                && !STATUS_SUCCESS.equals(status)
                && !STATUS_REJECTION.equals(status)) {
            throw new IllegalArgumentException("Unsupported status: " + status);
        }
        this.status = status;
    }
}
