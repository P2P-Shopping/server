package com.p2ps.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionTypeTest {

    @Test
    void fromValueMatchesDifferentFormats() {
        assertEquals(ActionType.ADD, ActionType.fromValue("add"));
        assertEquals(ActionType.ADD, ActionType.fromValue("  add  "));
        assertEquals(ActionType.UPDATE, ActionType.fromValue("update"));
        assertEquals(ActionType.CHECK_OFF, ActionType.fromValue("check-off"));
        assertEquals(ActionType.CHECK_OFF, ActionType.fromValue("check_off"));
        assertEquals(ActionType.CHECK_OFF, ActionType.fromValue("CHECK OFF"));
        assertEquals(ActionType.TYPING, ActionType.fromValue("typing"));
    }

    @Test
    void fromValueFallsBackToUnknownForInvalidInput() {
        assertEquals(ActionType.UNKNOWN, ActionType.fromValue(null));
        assertEquals(ActionType.UNKNOWN, ActionType.fromValue(""));
        assertEquals(ActionType.UNKNOWN, ActionType.fromValue("   "));
        assertEquals(ActionType.UNKNOWN, ActionType.fromValue("not-a-real-action"));
    }

    @Test
    void exposesSerializedValues() {
        assertEquals("ADD", ActionType.ADD.getValue());
        assertEquals("UNKNOWN", ActionType.UNKNOWN.getValue());
    }
}
