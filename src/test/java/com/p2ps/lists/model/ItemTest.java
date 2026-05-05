package com.p2ps.lists.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ItemTest {

    @Test
    void testConstructorAndGettersSetters() {
        ShoppingList list = new ShoppingList();
        Item item = new Item("Apple", list);
        
        assertThat(item.getName()).isEqualTo("Apple");
        assertThat(item.getShoppingList()).isEqualTo(list);
        
        UUID id = UUID.randomUUID();
        item.setId(id);
        item.setChecked(true);
        item.setBrand("Organic");
        item.setQuantity("5");
        item.setPrice(new BigDecimal("2.50"));
        item.setCategory("Fruit");
        item.setRecurrent(true);
        item.setLastUpdatedTimestamp(12345L);
        item.setVersion(1L);
        
        assertThat(item.getId()).isEqualTo(id);
        assertThat(item.isChecked()).isTrue();
        assertThat(item.getBrand()).isEqualTo("Organic");
        assertThat(item.getQuantity()).isEqualTo("5");
        assertThat(item.getPrice()).isEqualTo(new BigDecimal("2.50"));
        assertThat(item.getCategory()).isEqualTo("Fruit");
        assertThat(item.isRecurrent()).isTrue();
        assertThat(item.getLastUpdatedTimestamp()).isEqualTo(12345L);
        assertThat(item.getVersion()).isEqualTo(1L);
    }

    @Test
    void testNoArgsConstructor() {
        Item item = new Item();
        assertThat(item).isNotNull();
    }
}
