package com.p2ps.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListUpdatePayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testGettersAndSetters() {
        ListUpdatePayload payload = new ListUpdatePayload();

        payload.setAction(ActionType.ADD);
        assertEquals(ActionType.ADD, payload.getAction());

        payload.setAction(ActionType.UPDATE);
        assertEquals(ActionType.UPDATE, payload.getAction());

        payload.setItemId("item-123");
        assertEquals("item-123", payload.getItemId());

        payload.setContent("Apples");
        assertEquals("Apples", payload.getContent());

        payload.setChecked(Boolean.TRUE);
        assertEquals(Boolean.TRUE, payload.getChecked());
    }

    @Test
    void testJsonRoundTrip() throws Exception {
        ListUpdatePayload original = new ListUpdatePayload();
        original.setAction(ActionType.ADD);
        original.setItemId("item-123");
        original.setContent("Apples");
        original.setChecked(Boolean.TRUE);

        String json = objectMapper.writeValueAsString(original);
        ListUpdatePayload deserialized = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(original.getAction(), deserialized.getAction());
        assertEquals(original.getItemId(), deserialized.getItemId());
        assertEquals(original.getContent(), deserialized.getContent());
        assertEquals(original.getChecked(), deserialized.getChecked());
    }

    @Test
    void testJsonDeserialization_ActionTypeAlias_MapsToTyping() throws Exception {
        String json = "{\"action_type\":\"typing\",\"itemId\":\"item-5\",\"content\":\"Still here\",\"isChecked\":true}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.TYPING, payload.getAction());
        assertEquals("item-5", payload.getItemId());
        assertEquals("Still here", payload.getContent());
        assertEquals(Boolean.TRUE, payload.getChecked());
    }

    @Test
    void testJsonDeserialization_CompletedAliasTrue_MapsToCheckedTrue() throws Exception {
        String json = "{\"action\":\"update\",\"itemId\":\"item-6\",\"content\":\"Done\",\"completed\":true}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.UPDATE, payload.getAction());
        assertEquals("item-6", payload.getItemId());
        assertEquals("Done", payload.getContent());
        assertEquals(Boolean.TRUE, payload.getChecked());
    }

    @Test
    void testJsonDeserialization_CompletedAliasFalse_MapsToCheckedFalse() throws Exception {
        String json = "{\"action\":\"update\",\"itemId\":\"item-7\",\"content\":\"Pending\",\"completed\":false}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.UPDATE, payload.getAction());
        assertEquals("item-7", payload.getItemId());
        assertEquals("Pending", payload.getContent());
        assertEquals(Boolean.FALSE, payload.getChecked());
    }

    @Test
    void testJsonDeserialization_UnrecognizedAction_MapsToUnknown() throws Exception {
        String json = "{\"action\":\"FOOBAR\",\"itemId\":\"item-1\",\"content\":\"Milk\"}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.UNKNOWN, payload.getAction());
        assertEquals("item-1", payload.getItemId());
        assertEquals("Milk", payload.getContent());
    }

    @Test
    void testJsonDeserialization_NullAction_DefaultsToUnknown() throws Exception {
        String json = "{\"action\":null,\"itemId\":\"item-2\",\"content\":\"Bread\"}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.UNKNOWN, payload.getAction());
    }

    @Test
    void testJsonDeserialization_OmittedAction_DefaultsToUnknown() throws Exception {
        String json = "{\"itemId\":\"item-3\",\"content\":\"Eggs\"}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.UNKNOWN, payload.getAction());
    }

    @Test
    void testJsonDeserialization_LowercaseAction_MapsToCorrectEnum() throws Exception {
        String json = "{\"action\":\"add\",\"itemId\":\"item-4\",\"content\":\"Butter\"}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.ADD, payload.getAction());
    }
}
