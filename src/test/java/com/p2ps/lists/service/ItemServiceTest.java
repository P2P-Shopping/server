package com.p2ps.lists.service;

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
import com.p2ps.auth.model.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ShoppingListRepository shoppingListRepository;

    @InjectMocks
    private ItemService itemService;

    private UUID listId;
    private UUID itemId;
    private String userEmail;
    private ShoppingList mockList;
    private Item mockItem;

    @BeforeEach
    void setUp() {
        listId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        userEmail = "test@user.com";

        Users owner = new Users();
        owner.setEmail(userEmail);

        mockList = new ShoppingList();
        mockList.setId(listId);
        mockList.setUser(owner);

        mockItem = new Item();
        mockItem.setId(itemId);
        mockItem.setShoppingList(mockList);
        mockItem.setName("Old Item");
        mockItem.setChecked(false);
    }

    @Test
    void addItemToList_Success() {
        ItemRequest req = new ItemRequest();
        req.setName("Milk");
        req.setPrice(BigDecimal.TEN);
        req.setIsRecurrent(true);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getName()).isEqualTo("Milk");
        assertThat(result.getPrice()).isEqualTo(BigDecimal.TEN);
        assertThat(result.isRecurrent()).isTrue();
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void addItemToList_ThrowsListValidationException_WhenNameIsNull() {
        ItemRequest req = new ItemRequest();
        req.setName("");

        assertThatThrownBy(() -> itemService.addItemToList(listId, req, userEmail))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("Item name cannot be empty");
    }

    @Test
    void addItemToList_ThrowsListValidationException_WhenPriceIsNegative() {
        ItemRequest req = new ItemRequest();
        req.setName("Milk");
        req.setPrice(new BigDecimal("-5.00"));

        assertThatThrownBy(() -> itemService.addItemToList(listId, req, userEmail))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("Price must be zero or positive");
    }

    @Test
    void addItemToList_ThrowsShoppingListNotFoundException() {
        ItemRequest req = new ItemRequest();
        req.setName("Milk");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.addItemToList(listId, req, userEmail))
                .isInstanceOf(ShoppingListNotFoundException.class);
    }

    @Test
    void addItemToList_ThrowsListAccessDeniedException_WhenWrongUser() {
        ItemRequest req = new ItemRequest();
        req.setName("Milk");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));

        assertThatThrownBy(() -> itemService.addItemToList(listId, req, "hacker@user.com"))
                .isInstanceOf(ListAccessDeniedException.class);
    }

    @Test
    void updateItem_Success() {
        ItemRequest req = new ItemRequest();
        req.setName("New Milk");
        req.setIsChecked(true);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItem(itemId, req, userEmail);

        assertThat(result.getName()).isEqualTo("New Milk");
        assertThat(result.isChecked()).isTrue();
        verify(itemRepository).save(mockItem);
    }

    @Test
    void updateItem_ThrowsItemNotFoundException() {
        ItemRequest req = new ItemRequest();
        req.setName("Test");

        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.updateItem(itemId, req, userEmail))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void updateItem_ThrowsListAccessDeniedException_WhenWrongUser() {
        ItemRequest req = new ItemRequest();
        req.setName("Test");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> itemService.updateItem(itemId, req, "hacker@user.com"))
                .isInstanceOf(ListAccessDeniedException.class);
    }

    @Test
    void updateItem_ThrowsListValidationException_WhenEmptyName() {
        ItemRequest req = new ItemRequest();
        req.setName("  "); // Empty space

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> itemService.updateItem(itemId, req, userEmail))
                .isInstanceOf(ListValidationException.class);
    }

    @Test
    void updateItemStatus_Success() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItemStatus(itemId, true, 12345L);

        assertThat(result.isChecked()).isTrue();
        verify(itemRepository).save(mockItem);
    }

    @Test
    void deleteItem_Success() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        itemService.deleteItem(itemId, userEmail);

        verify(itemRepository).delete(mockItem);
    }

    @Test
    void deleteItem_ThrowsListAccessDeniedException_WhenWrongUser() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> itemService.deleteItem(itemId, "hacker@user.com"))
                .isInstanceOf(ListAccessDeniedException.class);

        verify(itemRepository, never()).delete(any(Item.class));
    }

    @Test
    void addItemsToList_ReturnsEmptyList_WhenRequestIsNull() {
        List<ItemDTO> result = itemService.addItemsToList(listId, null, userEmail);
        assertThat(result).isEmpty();
        verifyNoInteractions(itemRepository);
    }

    @Test
    void addItemsToList_Success() {
        ItemRequest req1 = new ItemRequest(); req1.setName("Item 1");
        ItemRequest req2 = new ItemRequest(); req2.setName("Item 2");
        List<ItemRequest> requests = List.of(req1, req2);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ItemDTO> result = itemService.addItemsToList(listId, requests, userEmail);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Item 1");
        verify(itemRepository).saveAll(anyList());
    }

    @Test
    void addItemsToList_ThrowsValidationException_WhenOneItemIsInvalid() {
        ItemRequest req1 = new ItemRequest(); req1.setName("Valid");
        ItemRequest req2 = new ItemRequest(); req2.setName(""); // Invalid

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));

        List<ItemRequest> requests = List.of(req1, req2);
        assertThatThrownBy(() -> itemService.addItemsToList(listId, requests, userEmail))
                .isInstanceOf(ListValidationException.class);

        verify(itemRepository, never()).saveAll(anyList());
    }
}