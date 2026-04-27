package com.p2ps.lists.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import java.math.BigDecimal;

import com.p2ps.lists.dto.ImportItemsRequestDTO;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ListCategory;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ItemRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock
    private ShoppingListRepository shoppingListRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ItemRepository itemRepository;

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
        savedList.setCategory(ListCategory.NORMAL);

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenReturn(savedList);

        ShoppingListDTO result = shoppingListService.createList("Weekly groceries", userEmail, ListCategory.NORMAL, null);

        assertEquals(listId, result.getId());
        assertEquals("Weekly groceries", result.getTitle());
        assertEquals(ListCategory.NORMAL, result.getCategory());
        verify(shoppingListRepository).save(any(ShoppingList.class));
    }

    @Test
    void createListShouldThrowWhenUserDoesNotExist() {
        String userEmail = "missing@example.com";
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.empty());

        assertThrows(ListUserNotFoundException.class,
                () -> shoppingListService.createList("Weekly groceries", userEmail, ListCategory.NORMAL, null));

        verify(shoppingListRepository, never()).save(any(ShoppingList.class));
    }

    @Test
    void updateListShouldUpdateFieldsAndSave() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        UUID listId = UUID.randomUUID();

        ShoppingList existingList = new ShoppingList();
        existingList.setId(listId);
        existingList.setTitle("Old Title");
        existingList.setUser(user);
        
        ShoppingListDTO updateDto = new ShoppingListDTO();
        updateDto.setTitle("New Title");
        updateDto.setCategory(ListCategory.FREQUENT);
        updateDto.setSubcategory("Alimente");
        updateDto.setFinalStore("Kaufland");
        
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(existingList));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        ShoppingListDTO result = shoppingListService.updateList(listId, updateDto, userEmail);
        
        assertEquals("New Title", result.getTitle());
        assertEquals(ListCategory.FREQUENT, result.getCategory());
        assertEquals("Alimente", result.getSubcategory());
        assertEquals("Kaufland", result.getFinalStore());
        verify(shoppingListRepository).save(existingList);
    }

    @Test
    void updateListShouldResetOptionalFieldsWhenEmptyString() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        UUID listId = UUID.randomUUID();

        ShoppingList existingList = new ShoppingList();
        existingList.setId(listId);
        existingList.setTitle("Old Title");
        existingList.setUser(user);
        existingList.setSubcategory("Alimente");
        existingList.setFinalStore("Kaufland");
        
        ShoppingListDTO updateDto = new ShoppingListDTO();
        updateDto.setSubcategory("");
        updateDto.setFinalStore("");
        
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(existingList));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        ShoppingListDTO result = shoppingListService.updateList(listId, updateDto, userEmail);
        
        assertNull(result.getSubcategory());
        assertNull(result.getFinalStore());
        verify(shoppingListRepository).save(existingList);
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

        when(shoppingListRepository.findAccessibleByEmail(userEmail)).thenReturn(List.of(firstList, secondList));

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

        when(shoppingListRepository.findAccessibleByEmail(userEmail)).thenReturn(List.of(list));

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

        assertEquals("Only the owner can delete this list", exception.getMessage());
        verify(shoppingListRepository, never()).delete(any(ShoppingList.class));
    }

    @Test
    void getListByIdShouldReturnMappedDTO() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setTitle("My List");
        list.setUser(user);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));

        ShoppingListDTO result = shoppingListService.getListById(listId, userEmail);

        assertEquals(listId, result.getId());
        assertEquals("My List", result.getTitle());
    }

    @Test
    void getListByIdShouldThrowWhenNotFound() {
        UUID listId = UUID.randomUUID();
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.empty());

        assertThrows(ShoppingListNotFoundException.class,
                () -> shoppingListService.getListById(listId, "ana@example.com"));
    }

    @Test
    void getListByIdShouldThrowWhenAccessDenied() {
        UUID listId = UUID.randomUUID();
        Users owner = new Users("owner@example.com", "secret", "Owner", "User");
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(owner);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));

        assertThrows(ListAccessDeniedException.class,
                () -> shoppingListService.getListById(listId, "ana@example.com"));
    }
    
    @Test
    void importItemsShouldCopyAllItemsWhenNoItemIdsProvided() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");

        UUID currentListId = UUID.randomUUID();
        ShoppingList currentList = new ShoppingList();
        currentList.setId(currentListId);
        currentList.setUser(user);

        UUID sourceListId = UUID.randomUUID();
        ShoppingList sourceList = new ShoppingList();
        sourceList.setId(sourceListId);
        sourceList.setUser(user);

        Item item1 = new Item();
        item1.setId(UUID.randomUUID());
        item1.setName("Item 1");

        Item item2 = new Item();
        item2.setId(UUID.randomUUID());
        item2.setName("Item 2");

        sourceList.getItems().addAll(List.of(item1, item2));

        ImportItemsRequestDTO request = new ImportItemsRequestDTO();
        request.setSourceListId(sourceListId);

        when(shoppingListRepository.findById(currentListId)).thenReturn(Optional.of(currentList));
        when(shoppingListRepository.findById(sourceListId)).thenReturn(Optional.of(sourceList));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListDTO result = shoppingListService.importItems(currentListId, request, userEmail);

        verify(itemRepository, times(2)).save(any(Item.class));
        assertEquals(2, result.getItems().size());
        assertTrue(result.getItems().stream().anyMatch(i -> i.getName().equals("Item 1")));
        assertTrue(result.getItems().stream().anyMatch(i -> i.getName().equals("Item 2")));
    }

    @Test
    void importItemsShouldCopyOnlySpecificItemsWhenItemIdsProvided() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");

        UUID currentListId = UUID.randomUUID();
        ShoppingList currentList = new ShoppingList();
        currentList.setId(currentListId);
        currentList.setUser(user);

        UUID sourceListId = UUID.randomUUID();
        ShoppingList sourceList = new ShoppingList();
        sourceList.setId(sourceListId);
        sourceList.setUser(user);

        Item item1 = new Item();
        item1.setId(UUID.randomUUID());
        item1.setName("Item 1");

        Item item2 = new Item();
        item2.setId(UUID.randomUUID());
        item2.setName("Item 2");

        sourceList.getItems().addAll(List.of(item1, item2));

        ImportItemsRequestDTO request = new ImportItemsRequestDTO();
        request.setSourceListId(sourceListId);
        request.setItemIds(List.of(item1.getId()));

        when(shoppingListRepository.findById(currentListId)).thenReturn(Optional.of(currentList));
        when(shoppingListRepository.findById(sourceListId)).thenReturn(Optional.of(sourceList));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListDTO result = shoppingListService.importItems(currentListId, request, userEmail);

        verify(itemRepository, times(1)).save(any(Item.class));
        assertEquals(1, result.getItems().size());
        assertEquals("Item 1", result.getItems().get(0).getName());
    }

    @Test
    void importItemsShouldThrowWhenSourceListIdIsNull() {
        ImportItemsRequestDTO request = new ImportItemsRequestDTO();
        // sourceListId is null

        UUID currentListId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
            () -> shoppingListService.importItems(currentListId, request, "ana@example.com"));
    }

    @Test
    void importItemsShouldThrowWhenImportingToSameList() {
        UUID sameId = UUID.randomUUID();
        ImportItemsRequestDTO request = new ImportItemsRequestDTO();
        request.setSourceListId(sameId);

        assertThrows(IllegalArgumentException.class,
            () -> shoppingListService.importItems(sameId, request, "ana@example.com"));
    }

    @Test
    void mapToDTOShouldIncludeItems() {
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());
        list.setTitle("Groceries");

        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName("Milk");
        item.setChecked(false);
        item.setPrice(new BigDecimal("1.5"));
        item.setQuantity("2");


        list.setItems(List.of(item));

        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        list.setUser(user);
        when(shoppingListRepository.findById(list.getId())).thenReturn(Optional.of(list));

        ShoppingListDTO result = shoppingListService.getListById(list.getId(), userEmail);

        assertEquals(1, result.getItems().size());
        assertEquals("Milk", result.getItems().get(0).getName());
        assertEquals(new BigDecimal("1.5"), result.getItems().get(0).getPrice());
    }
    @Test
    void shareListShouldAddCollaboratorWhenCalledByOwner() {
        String ownerEmail = "owner@example.com";
        String collabEmail = "collab@example.com";
        UUID listId = UUID.randomUUID();
        
        Users owner = new Users(ownerEmail, "pass", "Owner", "User");
        Users collaborator = new Users(collabEmail, "pass", "Collab", "User");
        
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(owner);
        
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(userRepository.findByEmail(collabEmail)).thenReturn(Optional.of(collaborator));
        
        shoppingListService.shareList(listId, collabEmail, ownerEmail);
        
        assertTrue(list.getCollaborators().contains(collaborator));
        verify(shoppingListRepository).save(list);
    }

    @Test
    void shareListShouldThrowWhenCalledByNonOwner() {
        String ownerEmail = "owner@example.com";
        String otherEmail = "other@example.com";
        UUID listId = UUID.randomUUID();
        
        Users owner = new Users(ownerEmail, "pass", "Owner", "User");
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(owner);
        
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        
        assertThrows(ListAccessDeniedException.class, 
                () -> shoppingListService.shareList(listId, "some@email.com", otherEmail));
    }

    @Test
    void shareListShouldThrowWhenSharingWithSelf() {
        String ownerEmail = "owner@example.com";
        UUID listId = UUID.randomUUID();
        
        Users owner = new Users(ownerEmail, "pass", "Owner", "User");
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(owner);
        
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        
        assertThrows(IllegalArgumentException.class, 
                () -> shoppingListService.shareList(listId, ownerEmail, ownerEmail));
    }

    @Test
    void shareListShouldThrowWhenCollaboratorNotFound() {
        String ownerEmail = "owner@example.com";
        String unknownEmail = "unknown@example.com";
        UUID listId = UUID.randomUUID();
        
        Users owner = new Users(ownerEmail, "pass", "Owner", "User");
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(owner);
        
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(userRepository.findByEmail(unknownEmail)).thenReturn(Optional.empty());
        
        assertThrows(ListUserNotFoundException.class, 
                () -> shoppingListService.shareList(listId, unknownEmail, ownerEmail));
    }

    @Test
    void getListByIdShouldAllowCollaboratorAccess() {
        String collabEmail = "collab@example.com";
        Users owner = new Users("owner@example.com", "pass", "Owner", "User");
        Users collaborator = new Users(collabEmail, "pass", "Collab", "User");
        
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(owner);
        list.getCollaborators().add(collaborator);
        
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        
        ShoppingListDTO result = shoppingListService.getListById(listId, collabEmail);
        
        assertEquals(listId, result.getId());
    }
}
