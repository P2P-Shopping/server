package com.p2ps.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ActionType {

    ADD("ADD"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    CHECK_OFF("CHECK_OFF"),
    TYPING("TYPING"),
    UNKNOWN("UNKNOWN");

    private final String value;
    private final String normalizedValue;
    private final String normalizedName;

    ActionType(String value) {
        this.value = value;
        this.normalizedValue = normalize(value);
        this.normalizedName = normalize(name());
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ActionType fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }

        String normalizedValue = normalize(value);
        for (ActionType action : ActionType.values()) {
            if (action.normalizedValue.equals(normalizedValue) || action.normalizedName.equals(normalizedValue)) {
                return action;
            }
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
