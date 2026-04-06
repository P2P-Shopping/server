package com.p2ps.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * Data Transfer Object representing a real-time modification to a shopping list.
 * Defines the standard structure for messages broadcasted to list-specific WebSocket rooms.
 */
public class ListUpdatePayload {

    private ActionType action = ActionType.UNKNOWN;
    private String itemId;
    private String content;
    private Boolean checked;

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

    @JsonIgnore
    public ActionType getActionType() {
        return action;
    }

    @JsonIgnore
    public void setActionType(ActionType action) {
        setAction(action);
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

    /**
     * Gets whether the item is checked off.
     * @return true when the item is marked complete, false otherwise
     */
    public Boolean getChecked() {
        return checked;
    }

    @JsonAlias({"isChecked", "completed"})
    public void setChecked(Boolean checked) {
        this.checked = checked;
    }
}
