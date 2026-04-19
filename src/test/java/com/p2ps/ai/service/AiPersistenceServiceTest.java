package com.p2ps.ai.service;

import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.service.ItemService;
import com.p2ps.lists.service.ShoppingListService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPersistenceServiceTest {

    @Mock
    private ShoppingListService shoppingListService;

    @Mock
    private ItemService itemService;

    @InjectMocks
    private AiPersistenceService aiPersistenceService;

    @Test
    void createListAndPopulateItems_WithExistingList() {
        // Arrange
        UUID existingListId = UUID.randomUUID();
        ParsedItemResponse item = new ParsedItemResponse();
        item.setName("Milk");
        item.setQuantity(1.0);
        item.setUnit("L");

        // Act
        aiPersistenceService.createListAndPopulateItems(existingListId, null, List.of(item), "user@test.com");

        // Assert
        verify(shoppingListService, never()).createList(anyString(), anyString()); // Nu s-a creat listă nouă
        verify(itemService, times(1)).addItemsToList(eq(existingListId), anyList(), eq("user@test.com"));
    }

    @Test
    void createListAndPopulateItems_CreatesNewListWhenIdIsNull() {
        // Arrange
        ParsedItemResponse item = new ParsedItemResponse();
        item.setName("Bread");

        UUID newListId = UUID.randomUUID();
        ShoppingListDTO mockNewList = new ShoppingListDTO();
        mockNewList.setId(newListId);

        when(shoppingListService.createList(anyString(), eq("user@test.com"))).thenReturn(mockNewList);

        // Act
        aiPersistenceService.createListAndPopulateItems(null, "My New List", List.of(item), "user@test.com");

        // Assert
        verify(shoppingListService, times(1)).createList(eq("My New List"), eq("user@test.com"));
        verify(itemService, times(1)).addItemsToList(eq(newListId), anyList(), eq("user@test.com"));
    }
}