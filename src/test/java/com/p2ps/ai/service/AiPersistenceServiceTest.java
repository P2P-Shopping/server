package com.p2ps.ai.service;

import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.model.ListCategory;
import com.p2ps.lists.service.ItemService;
import com.p2ps.lists.service.ShoppingListService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Captor
    private ArgumentCaptor<List<ItemRequest>> itemRequestCaptor;

    @Test
    void createList_withExistingList_skipsCreationAndMapsCorrectly() {
        // Arrange
        UUID existingListId = UUID.randomUUID();
        ParsedItemResponse item = new ParsedItemResponse();
        item.setGenericName("Milk ");
        item.setQuantity(1.5);
        item.setUnit("L");

        // Act
        aiPersistenceService.createListAndPopulateItems(existingListId, null, List.of(item), "user@test.com");

        // Assert
        verify(shoppingListService, never()).createList(anyString(), anyString(), any(), any());

        verify(itemService, times(1)).addItemsToList(eq(existingListId), itemRequestCaptor.capture(), eq("user@test.com"));

        List<ItemRequest> mappedItems = itemRequestCaptor.getValue();
        assertThat(mappedItems).hasSize(1);

        ItemRequest savedItem = mappedItems.getFirst();
        assertThat(savedItem.getName()).isEqualTo("Milk"); // Trebuie să fie tăiat de trim()
        assertThat(savedItem.getQuantity()).isEqualTo("1.5 L");
        assertThat(savedItem.getCategory()).isEqualTo("AI Generated");
    }

    @Test
    void createList_withNullIdAndValidTitle_createsNewList() {
        // Arrange
        ParsedItemResponse item = new ParsedItemResponse();
        item.setGenericName("Bread");

        UUID newListId = UUID.randomUUID();
        ShoppingListDTO mockNewList = new ShoppingListDTO();
        mockNewList.setId(newListId);

        when(shoppingListService.createList(eq("My Party List"), eq("user@test.com"), eq(ListCategory.NORMAL), isNull()))
                .thenReturn(mockNewList);

        // Act
        aiPersistenceService.createListAndPopulateItems(null, "My Party List", List.of(item), "user@test.com");

        // Assert
        verify(shoppingListService, times(1)).createList(eq("My Party List"), eq("user@test.com"), eq(ListCategory.NORMAL), isNull());
        verify(itemService, times(1)).addItemsToList(eq(newListId), anyList(), eq("user@test.com"));
    }

    @Test
    void createList_withNullIdAndBlankTitle_generatesDefaultTitleAndHandlesNullQuantity() {
        // Arrange
        ParsedItemResponse item = new ParsedItemResponse();
        item.setGenericName("Water");

        UUID newListId = UUID.randomUUID();
        ShoppingListDTO mockNewList = new ShoppingListDTO();
        mockNewList.setId(newListId);

        String expectedDefaultTitle = "AI Generated " + LocalDate.now();
        when(shoppingListService.createList(eq(expectedDefaultTitle), eq("user@test.com"), eq(ListCategory.NORMAL), isNull()))
                .thenReturn(mockNewList);

        // Act
        aiPersistenceService.createListAndPopulateItems(null, "   ", List.of(item), "user@test.com");

        // Assert
        verify(shoppingListService, times(1)).createList(eq(expectedDefaultTitle), eq("user@test.com"), eq(ListCategory.NORMAL), isNull());

        verify(itemService, times(1)).addItemsToList(eq(newListId), itemRequestCaptor.capture(), eq("user@test.com"));
        ItemRequest savedItem = itemRequestCaptor.getValue().getFirst();
        assertThat(savedItem.getQuantity()).isEmpty(); // "".trim() -> ""
    }

    @Test
    void createList_withEmptyItems_doesNotCallItemService() {
        // Arrange
        UUID listId = UUID.randomUUID();

        // Act
        aiPersistenceService.createListAndPopulateItems(listId, "Title", Collections.emptyList(), "user@test.com");

        // Assert
        // Dacă primește o listă goală, nu ar trebui să apeleze baza de date pentru salvare itemi
        verify(itemService, never()).addItemsToList(any(), any(), any());
    }
}