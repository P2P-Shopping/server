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
import java.util.Collections;
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

    @Test
    void addItemToList_emptyNameThrows() {
        ItemRequest req = new ItemRequest();
        req.setName(" ");
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> itemService.addItemToList(id, req, "u@e")).isInstanceOf(ListValidationException.class);
    }

    @Test
    void addItemToList_listNotFound() {
        UUID id = UUID.randomUUID();
        ItemRequest req = new ItemRequest();
        req.setName("Eggs");
        when(shoppingListRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> itemService.addItemToList(id, req, "u@e")).isInstanceOf(ShoppingListNotFoundException.class);
    }

    @Test
    void addItemToList_accessDenied() {
        UUID id = UUID.randomUUID();
        ItemRequest req = new ItemRequest();
        req.setName("Eggs");
        ShoppingList list = new ShoppingList();
        Users u = new Users();
        u.setEmail("other@e");
        list.setUser(u);
        when(shoppingListRepository.findById(id)).thenReturn(Optional.of(list));
        assertThatThrownBy(() -> itemService.addItemToList(id, req, "u@e")).isInstanceOf(ListAccessDeniedException.class);
    }

    @Test
    void addItemToList_success() {
        UUID id = UUID.randomUUID();
        ItemRequest req = new ItemRequest();
        req.setName("Milk");
        req.setBrand("BrandX");
        req.setQuantity("1");
        req.setPrice(new BigDecimal("1.50"));
        req.setCategory("Food");
        req.setIsRecurrent(true);

        ShoppingList list = new ShoppingList();
        Users u = new Users();
        u.setEmail("u@e");
        list.setUser(u);

        when(shoppingListRepository.findById(id)).thenReturn(Optional.of(list));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item i = invocation.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });

        ItemDTO dto = itemService.addItemToList(id, req, "u@e");
        assertThat(dto.getName()).isEqualTo("Milk");
        assertThat(dto.getBrand()).isEqualTo("BrandX");
        assertThat(dto.getPrice()).isEqualTo(new BigDecimal("1.50"));
        assertThat(dto.isRecurrent()).isTrue();
    }

    @Test
    void addItemsToList_emptyRequests_returnsEmpty() {
        UUID id = UUID.randomUUID();
        var res = itemService.addItemsToList(id, Collections.emptyList(), "u@e");
        assertThat(res).isEmpty();
    }
}
