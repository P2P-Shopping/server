package com.p2ps.lists.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ItemTest {

    @Test
    void testOnCreate() {
        Item item = new Item();
        item.onCreate();
        
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getLastUpdatedTimestamp()).isNotNull();
        assertThat(item.getCreatedAt()).isEqualTo(item.getLastUpdatedTimestamp());
    }

    @Test
    void testGettersAndSetters() {
        Item item = new Item();
        UUID id = UUID.randomUUID();
        String name = "Test Item";
        String brand = "Test Brand";
        String quantity = "1 kg";
        BigDecimal price = new BigDecimal("10.50");
        String category = "Produce";
        ShoppingList list = new ShoppingList();
        
        item.setId(id);
        item.setName(name);
        item.setChecked(true);
        item.setBrand(brand);
        item.setQuantity(quantity);
        item.setPrice(price);
        item.setCategory(category);
        item.setRecurrent(true);
        item.setShoppingList(list);
        item.setVersion(1L);

        assertThat(item.getId()).isEqualTo(id);
        assertThat(item.getName()).isEqualTo(name);
        assertThat(item.isChecked()).isTrue();
        assertThat(item.getBrand()).isEqualTo(brand);
        assertThat(item.getQuantity()).isEqualTo(quantity);
        assertThat(item.getPrice()).isEqualTo(price);
        assertThat(item.getCategory()).isEqualTo(category);
        assertThat(item.isRecurrent()).isTrue();
        assertThat(item.getShoppingList()).isEqualTo(list);
        assertThat(item.getVersion()).isEqualTo(1L);
    }
    
}
