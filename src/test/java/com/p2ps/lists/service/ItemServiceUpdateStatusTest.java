package com.p2ps.lists.service;

import com.p2ps.auth.model.Users;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ItemRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceUpdateStatusTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ShoppingListRepository shoppingListRepository;

    @InjectMocks
    private ItemService itemService;

    @Test
    void updateItemStatusShouldPersistCheckedStateAndTimestamp() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long before = System.currentTimeMillis();
        ItemDTO result = itemService.updateItemStatus(itemId, true, 123L);
        long after = System.currentTimeMillis();

        assertEquals(true, result.isChecked());
        assertTrue(result.getLastUpdatedTimestamp() >= before);
        assertTrue(result.getLastUpdatedTimestamp() <= after);
    }

    @Test
    void updateItemStatusShouldUseCurrentTimeWhenClientTimestampIsNull() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long before = System.currentTimeMillis();
        ItemDTO result = itemService.updateItemStatus(itemId, true, null);
        long after = System.currentTimeMillis();

        assertEquals(true, result.isChecked());
        assertTrue(result.getLastUpdatedTimestamp() >= before);
        assertTrue(result.getLastUpdatedTimestamp() <= after);
    }

    @Test
    void updateItemStatusShouldThrowWhenItemDoesNotExist() {
        UUID itemId = UUID.randomUUID();

        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class,
                () -> itemService.updateItemStatus(itemId, true, 123L));
    }

    private Item buildItem() {
        Users user = new Users("ana@example.com", "secret", "Ana", "Ionescu");
        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setId(UUID.randomUUID());
        shoppingList.setTitle("Weekly groceries");
        shoppingList.setUser(user);

        Item item = new Item();
        item.setShoppingList(shoppingList);
        item.setChecked(false);
        item.setLastUpdatedTimestamp(1L);
        return item;
    }
}
