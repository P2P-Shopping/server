package com.p2ps.lists.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock
    private ShoppingListRepository shoppingListRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ShoppingListService shoppingListService;

    @Test
    void createListShouldPersistListForExistingUser() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        ShoppingList savedList = new ShoppingList();
        UUID listId = UUID.randomUUID();
        savedList.setId(listId);
        savedList.setTitle("Weekly groceries");
        savedList.setUser(user);

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenReturn(savedList);

        ShoppingListDTO result = shoppingListService.createList("Weekly groceries", userEmail);

        assertEquals(listId, result.getId());
        assertEquals("Weekly groceries", result.getTitle());
        verify(shoppingListRepository).save(any(ShoppingList.class));
    }

    @Test
    void createListShouldThrowWhenUserDoesNotExist() {
        String userEmail = "missing@example.com";
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.empty());

        assertThrows(ListUserNotFoundException.class,
                () -> shoppingListService.createList("Weekly groceries", userEmail));

        verify(shoppingListRepository, never()).save(any(ShoppingList.class));
    }

    @Test
    void getUserListsShouldMapRepositoryResults() {
        String userEmail = "ana@example.com";
        ShoppingList firstList = new ShoppingList();
        firstList.setId(UUID.randomUUID());
        firstList.setTitle("Groceries");

        ShoppingList secondList = new ShoppingList();
        secondList.setId(UUID.randomUUID());
        secondList.setTitle("Hardware");

        when(shoppingListRepository.findByUser_Email(userEmail)).thenReturn(List.of(firstList, secondList));

        List<ShoppingListDTO> result = shoppingListService.getUserLists(userEmail);

        assertEquals(2, result.size());
        assertEquals("Groceries", result.get(0).getTitle());
        assertEquals("Hardware", result.get(1).getTitle());
        assertTrue(result.stream().map(ShoppingListDTO::getId).toList().contains(firstList.getId()));
        assertTrue(result.stream().map(ShoppingListDTO::getId).toList().contains(secondList.getId()));
    }

    @Test
    void getUserListsShouldReturnEmptyItemsWhenListHasNoCollection() {
        String userEmail = "ana@example.com";
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());
        list.setTitle("Groceries");
        list.setItems(null);

        when(shoppingListRepository.findByUser_Email(userEmail)).thenReturn(List.of(list));

        List<ShoppingListDTO> result = shoppingListService.getUserLists(userEmail);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getItems().isEmpty());
    }

    @Test
    void deleteListShouldRemoveOwnedList() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));

        shoppingListService.deleteList(listId, userEmail);

        verify(shoppingListRepository).delete(same(list));
    }

    @Test
    void deleteListShouldThrowWhenListDoesNotExist() {
        UUID listId = UUID.randomUUID();
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.empty());

        ShoppingListNotFoundException exception = assertThrows(
                ShoppingListNotFoundException.class,
                () -> shoppingListService.deleteList(listId, "ana@example.com")
        );

        assertEquals("Shopping list not found", exception.getMessage());
        verify(shoppingListRepository, never()).delete(any(ShoppingList.class));
    }

    @Test
    void deleteListShouldThrowWhenUserDoesNotOwnList() {
        UUID listId = UUID.randomUUID();
        Users owner = new Users("owner@example.com", "secret", "Owner", "User");
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(owner);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));

        ListAccessDeniedException exception = assertThrows(
                ListAccessDeniedException.class,
                () -> shoppingListService.deleteList(listId, "ana@example.com")
        );

        assertEquals("You do not have permission to delete this list", exception.getMessage());
        verify(shoppingListRepository, never()).delete(any(ShoppingList.class));
    }
}
