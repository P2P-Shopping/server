package com.p2ps.lists.service;

import com.p2ps.auth.model.Users;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListValidationException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ItemRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ShoppingListRepository shoppingListRepository;

    @InjectMocks
    private ItemService itemService;

    @Test
    void addItemToListShouldThrowWhenNameIsBlank() {
        ItemRequest request = new ItemRequest();
        request.setName("   ");
        UUID listId = UUID.randomUUID();

        assertThrows(ListValidationException.class,
                () -> itemService.addItemToList(listId, request, "ana@example.com"));

        verify(shoppingListRepository, never()).findById(any(UUID.class));
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void addItemToListShouldThrowWhenListDoesNotExist() {
        UUID listId = UUID.randomUUID();
        ItemRequest request = buildCreateRequest();

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.empty());

        assertThrows(ShoppingListNotFoundException.class,
                () -> itemService.addItemToList(listId, request, "ana@example.com"));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void addItemToListShouldThrowWhenPriceIsNegative() {
        ItemRequest request = buildCreateRequest();
        request.setPrice(new BigDecimal("-1.00"));
        UUID listId = UUID.randomUUID();

        assertThrows(ListValidationException.class,
                () -> itemService.addItemToList(listId, request, "ana@example.com"));

        verify(shoppingListRepository, never()).findById(any(UUID.class));
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void addItemToListShouldThrowWhenUserDoesNotOwnList() {
        UUID listId = UUID.randomUUID();
        ShoppingList shoppingList = buildShoppingList("owner@example.com");
        ItemRequest request = buildCreateRequest();

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(shoppingList));

        assertThrows(ListAccessDeniedException.class,
                () -> itemService.addItemToList(listId, request, "other@example.com"));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void addItemToListShouldPersistAndMapItem() {
        UUID listId = UUID.randomUUID();
        ShoppingList shoppingList = buildShoppingList("ana@example.com");
        ItemRequest request = buildCreateRequest();
        Item savedItem = new Item();
        UUID itemId = UUID.randomUUID();
        savedItem.setId(itemId);
        savedItem.setName(request.getName());
        savedItem.setBrand(request.getBrand());
        savedItem.setQuantity(request.getQuantity());
        savedItem.setPrice(request.getPrice());
        savedItem.setCategory(request.getCategory());
        savedItem.setRecurrent(Boolean.TRUE.equals(request.getIsRecurrent()));
        savedItem.setShoppingList(shoppingList);
        savedItem.setLastUpdatedTimestamp(123456789L);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(shoppingList));
        when(itemRepository.save(any(Item.class))).thenReturn(savedItem);

        ItemDTO result = itemService.addItemToList(listId, request, "ana@example.com");

        assertEquals(itemId, result.getId());
        assertEquals("Milk", result.getName());
        assertEquals("Brand A", result.getBrand());
        assertEquals(new BigDecimal("12.50"), result.getPrice());
        assertTrue(result.isRecurrent());
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void updateItemShouldThrowWhenItemDoesNotExist() {
        UUID itemId = UUID.randomUUID();
        ItemRequest request = new ItemRequest();

        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class,
                () -> itemService.updateItem(itemId, request, "ana@example.com"));
    }

    @Test
    void updateItemShouldThrowWhenUserDoesNotOwnItem() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem("owner@example.com");
        ItemRequest request = new ItemRequest();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(ListAccessDeniedException.class,
                () -> itemService.updateItem(itemId, request, "other@example.com"));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void updateItemShouldThrowWhenNameIsBlank() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem("ana@example.com");
        ItemRequest request = new ItemRequest();
        request.setName("   ");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(ListValidationException.class,
                () -> itemService.updateItem(itemId, request, "ana@example.com"));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void updateItemShouldThrowWhenPriceIsNegative() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem("ana@example.com");
        ItemRequest request = new ItemRequest();
        request.setPrice(new BigDecimal("-4.10"));

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(ListValidationException.class,
                () -> itemService.updateItem(itemId, request, "ana@example.com"));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void updateItemShouldApplyRequestFields() {
        UUID itemId = UUID.randomUUID();
        Item existingItem = buildItem("ana@example.com");
        existingItem.setId(itemId);
        existingItem.setName("Milk");
        existingItem.setBrand("Old");
        existingItem.setQuantity("1");
        existingItem.setPrice(new BigDecimal("3.50"));
        existingItem.setCategory("Dairy");
        existingItem.setRecurrent(false);
        existingItem.setChecked(false);
        existingItem.setLastUpdatedTimestamp(10L);

        ItemRequest request = new ItemRequest();
        request.setName("Oat Milk");
        request.setBrand("New");
        request.setQuantity("2");
        request.setPrice(new BigDecimal("4.10"));
        request.setCategory("Plant-based");
        request.setIsRecurrent(true);
        request.setIsChecked(true);
        request.setTimestamp(999L);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(existingItem));
        when(itemRepository.save(existingItem)).thenReturn(existingItem);

        long beforeUpdate = System.currentTimeMillis();
        ItemDTO result = itemService.updateItem(itemId, request, "ana@example.com");

        assertEquals("Oat Milk", result.getName());
        assertEquals("New", result.getBrand());
        assertEquals("2", result.getQuantity());
        assertEquals(new BigDecimal("4.10"), result.getPrice());
        assertEquals("Plant-based", result.getCategory());
        assertTrue(result.isRecurrent());
        assertTrue(result.isChecked());
        assertTrue(result.getLastUpdatedTimestamp() >= beforeUpdate);

    }

    @Test
    void updateItemShouldAllowNullPriceAndKeepExistingValue() {
        UUID itemId = UUID.randomUUID();
        Item existingItem = buildItem("ana@example.com");
        existingItem.setId(itemId);
        existingItem.setName("Milk");
        existingItem.setBrand("Old");
        existingItem.setPrice(new BigDecimal("3.50"));
        existingItem.setLastUpdatedTimestamp(10L);

        ItemRequest request = new ItemRequest();
        request.setBrand("Updated Brand");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(existingItem));
        when(itemRepository.save(existingItem)).thenReturn(existingItem);

        ItemDTO result = itemService.updateItem(itemId, request, "ana@example.com");

        assertEquals(new BigDecimal("3.50"), result.getPrice());
        assertEquals("Updated Brand", result.getBrand());
    }

    @Test
    void updateItemStatusShouldUseServerTimestampWhenClientTimestampMissing() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem("ana@example.com");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long beforeUpdate = System.currentTimeMillis();
        ItemDTO result = itemService.updateItemStatus(itemId, true, null);

        assertTrue(result.isChecked());
        assertTrue(result.getLastUpdatedTimestamp() >= beforeUpdate);
    }

    @Test
    void deleteItemShouldThrowWhenItemDoesNotExist() {
        UUID itemId = UUID.randomUUID();

        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class,
                () -> itemService.deleteItem(itemId, "ana@example.com"));
    }

    @Test
    void deleteItemShouldThrowWhenUserDoesNotOwnItem() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem("owner@example.com");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(ListAccessDeniedException.class,
                () -> itemService.deleteItem(itemId, "other@example.com"));

        verify(itemRepository, never()).delete(any(Item.class));
    }

    @Test
    void deleteItemShouldRemoveOwnedItem() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem("ana@example.com");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        itemService.deleteItem(itemId, "ana@example.com");

        verify(itemRepository).delete(item);
    }

    private ItemRequest buildCreateRequest() {
        ItemRequest request = new ItemRequest();
        request.setName("Milk");
        request.setBrand("Brand A");
        request.setQuantity("2");
        request.setPrice(new BigDecimal("12.50"));
        request.setCategory("Dairy");
        request.setIsRecurrent(true);
        return request;
    }

    private ShoppingList buildShoppingList(String email) {
        Users user = new Users(email, "secret", "Ana", "Ionescu");
        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setId(UUID.randomUUID());
        shoppingList.setTitle("Weekly groceries");
        shoppingList.setUser(user);
        return shoppingList;
    }

    private Item buildItem(String email) {
        Item item = new Item();
        item.setShoppingList(buildShoppingList(email));
        item.setChecked(false);
        item.setRecurrent(false);
        return item;
    }
}
