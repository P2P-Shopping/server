package com.p2ps.lists.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShoppingListDTOTest {

    @Test
    void finalStoreAliasMethods_delegateToFinalStoreName() {
        ShoppingListDTO dto = new ShoppingListDTO();

        assertNull(dto.getFinalStore());

        dto.setFinalStore("Mega Mall");
        assertEquals("Mega Mall", dto.getFinalStore());
        assertEquals("Mega Mall", dto.getFinalStoreName());

        dto.setFinalStoreName("Kaufland");
        assertEquals("Kaufland", dto.getFinalStore());
    }
}

