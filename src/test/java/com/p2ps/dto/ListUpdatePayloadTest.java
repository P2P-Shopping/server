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

        payload.setItemId("item-123");
        assertEquals("item-123", payload.getItemId());

        payload.setContent("Apples");
        assertEquals("Apples", payload.getContent());

        payload.setClaimedBy("user@example.com");
        assertEquals("user@example.com", payload.getClaimedBy());
    }

    @Test
    void testJsonRoundTrip() throws Exception {
        ListUpdatePayload original = new ListUpdatePayload();
        original.setAction(ActionType.ADD);
        original.setItemId("item-123");
        original.setContent("Apples");
        original.setClaimedBy("user@example.com");

        String json = objectMapper.writeValueAsString(original);
        ListUpdatePayload deserialized = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(original.getAction(), deserialized.getAction());
        assertEquals(original.getItemId(), deserialized.getItemId());
        assertEquals(original.getContent(), deserialized.getContent());
        assertEquals(original.getClaimedBy(), deserialized.getClaimedBy());
    }

    @Test
    void testClaimItemDeserialization() throws Exception {
        String json = "{\"action\":\"CLAIM_ITEM\",\"itemId\":\"item-1\",\"claimedBy\":\"alice@test.com\"}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.CLAIM_ITEM, payload.getAction());
        assertEquals("item-1", payload.getItemId());
        assertEquals("alice@test.com", payload.getClaimedBy());
    }

    @Test
    void testUnclaimItemDeserialization() throws Exception {
        String json = "{\"action\":\"UNCLAIM_ITEM\",\"itemId\":\"item-1\"}";
        ListUpdatePayload payload = objectMapper.readValue(json, ListUpdatePayload.class);

        assertEquals(ActionType.UNCLAIM_ITEM, payload.getAction());
        assertNull(payload.getClaimedBy());
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
    void testStatusValidation() {
        ListUpdatePayload payload = new ListUpdatePayload();

        payload.setStatus(ListUpdatePayload.STATUS_SUCCESS);
        assertEquals(ListUpdatePayload.STATUS_SUCCESS, payload.getStatus());

        payload.setStatus(ListUpdatePayload.STATUS_REJECTION);
        assertEquals(ListUpdatePayload.STATUS_REJECTION, payload.getStatus());

        payload.setStatus(null);
        assertNull(payload.getStatus());

        assertThrows(IllegalArgumentException.class, () -> payload.setStatus("INVALID"));
    }
}

