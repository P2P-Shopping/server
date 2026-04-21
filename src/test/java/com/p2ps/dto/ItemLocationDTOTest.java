package com.p2ps.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLocationDTOTest {

    @Test
    void shouldExposeAllProperties() {
        ItemLocationDTO dto = new ItemLocationDTO(1.5d, 2.5d, true, 0.25d);

        assertEquals(1.5d, dto.getLat());
        assertEquals(2.5d, dto.getLon());
        assertTrue(dto.isLowConfidenceWarning());
        assertEquals(0.25d, dto.getConfidenceScore());

        dto.setLat(3.0d);
        dto.setLon(4.0d);
        dto.setLowConfidenceWarning(false);
        dto.setConfidenceScore(0.9d);

        assertEquals(3.0d, dto.getLat());
        assertEquals(4.0d, dto.getLon());
        assertFalse(dto.isLowConfidenceWarning());
        assertEquals(0.9d, dto.getConfidenceScore());
    }
}
