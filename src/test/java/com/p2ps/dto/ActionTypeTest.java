package com.p2ps.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionTypeTest {

    @Test
    void getValueExposesWireValues() {
        assertEquals("ADD", ActionType.ADD.getValue());
        assertEquals("UPDATE", ActionType.UPDATE.getValue());
        assertEquals("DELETE", ActionType.DELETE.getValue());
        assertEquals("CHECK_OFF", ActionType.CHECK_OFF.getValue());
        assertEquals("TYPING", ActionType.TYPING.getValue());
        assertEquals("UNKNOWN", ActionType.UNKNOWN.getValue());
    }

    @Test
    void fromValueAcceptsNormalizedInput() {
        assertEquals(ActionType.CHECK_OFF, ActionType.fromValue("check-off"));
        assertEquals(ActionType.CHECK_OFF, ActionType.fromValue("CHECK_OFF"));
        assertEquals(ActionType.ADD, ActionType.fromValue("add"));
        assertEquals(ActionType.UNKNOWN, ActionType.fromValue(null));
        assertEquals(ActionType.UNKNOWN, ActionType.fromValue("not-supported"));
    }
}
