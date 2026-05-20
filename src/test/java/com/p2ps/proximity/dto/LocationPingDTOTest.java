package com.p2ps.proximity.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocationPingDTOTest {

    @Test
    void shouldSetAndGetAllFields() {
        LocationPingDTO dto = new LocationPingDTO();
        dto.setDeviceId("device-001");
        dto.setLat(47.15);
        dto.setLng(27.59);
        dto.setTimestamp(1234567890L);
        dto.setFcmToken("token-abc");

        assertEquals("device-001", dto.getDeviceId());
        assertEquals(47.15, dto.getLat());
        assertEquals(27.59, dto.getLng());
        assertEquals(1234567890L, dto.getTimestamp());
        assertEquals("token-abc", dto.getFcmToken());
    }

    @Test
    void shouldHaveEqualsAndHashCode() {
        LocationPingDTO dto1 = new LocationPingDTO();
        dto1.setDeviceId("device-001");
        dto1.setLat(47.15);
        dto1.setLng(27.59);

        LocationPingDTO dto2 = new LocationPingDTO();
        dto2.setDeviceId("device-001");
        dto2.setLat(47.15);
        dto2.setLng(27.59);

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
}