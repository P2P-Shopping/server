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
        assertEquals("CLAIM_ITEM", ActionType.CLAIM_ITEM.getValue());
        assertEquals("UNCLAIM_ITEM", ActionType.UNCLAIM_ITEM.getValue());
        assertEquals("TYPING", ActionType.TYPING.getValue());
        assertEquals("UNKNOWN", ActionType.UNKNOWN.getValue());
    }

    @Test
    void fromValueAcceptsNormalizedInput() {
        assertEquals(ActionType.CHECK_OFF, ActionType.fromValue("check-off"));
        assertEquals(ActionType.CHECK_OFF, ActionType.fromValue("CHECK_OFF"));
        assertEquals(ActionType.CLAIM_ITEM, ActionType.fromValue("claim-item"));
        assertEquals(ActionType.CLAIM_ITEM, ActionType.fromValue("CLAIM_ITEM"));
        assertEquals(ActionType.UNCLAIM_ITEM, ActionType.fromValue("unclaim-item"));
        assertEquals(ActionType.UNCLAIM_ITEM, ActionType.fromValue("UNCLAIM_ITEM"));
        assertEquals(ActionType.ADD, ActionType.fromValue("add"));
        assertEquals(ActionType.UNKNOWN, ActionType.fromValue(null));
        assertEquals(ActionType.UNKNOWN, ActionType.fromValue("not-supported"));
    }
}
