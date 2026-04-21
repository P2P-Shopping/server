package com.p2ps.telemetry.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryRequestTest {

    @Test
    void shouldExposeRequestFields() {
        TelemetryRequest request = new TelemetryRequest();
        UUID storeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        request.setStoreId(storeId);
        request.setItemId(itemId);
        request.setLat(47.1d);
        request.setLon(27.5d);
        request.setAccuracy(2.4d);

        assertEquals(storeId, request.getStoreId());
        assertEquals(itemId, request.getItemId());
        assertEquals(47.1d, request.getLat());
        assertEquals(27.5d, request.getLon());
        assertEquals(2.4d, request.getAccuracy());
    }
}
