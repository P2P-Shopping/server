package com.p2ps.proximity.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ActiveListLocationTest {

    @Test
    void shouldSetAndGetAllFields() {
        Instant now = Instant.now();
        ActiveListLocation loc = new ActiveListLocation();
        loc.setId("id-1");
        loc.setListId("list-1");
        loc.setItemId("item-1");
        loc.setOwnerEmail("test@example.com");
        loc.setCoordinates(new double[]{27.59, 47.15});
        loc.setCreatedAt(now);
        loc.setUpdatedAt(now);

        assertEquals("id-1", loc.getId());
        assertEquals("list-1", loc.getListId());
        assertEquals("item-1", loc.getItemId());
        assertEquals("test@example.com", loc.getOwnerEmail());
        assertArrayEquals(new double[]{27.59, 47.15}, loc.getCoordinates());
        assertEquals(now, loc.getCreatedAt());
        assertEquals(now, loc.getUpdatedAt());
    }
}