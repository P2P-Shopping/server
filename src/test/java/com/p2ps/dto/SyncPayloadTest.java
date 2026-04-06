package com.p2ps.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void inheritsListUpdatePayloadBehavior() throws Exception {
        SyncPayload payload = new SyncPayload();
        payload.setAction(ActionType.DELETE);
        payload.setItemId("item-1");
        payload.setContent("Milk");
        payload.setChecked(Boolean.TRUE);

        String json = objectMapper.writeValueAsString(payload);
        SyncPayload deserialized = objectMapper.readValue(json, SyncPayload.class);

        assertEquals(ActionType.DELETE, deserialized.getAction());
        assertEquals("item-1", deserialized.getItemId());
        assertEquals("Milk", deserialized.getContent());
        assertEquals(Boolean.TRUE, deserialized.getChecked());
    }
}
