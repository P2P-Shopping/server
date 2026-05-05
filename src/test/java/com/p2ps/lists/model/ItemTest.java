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
