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
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.catalog.model.ProductCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        user.setId(1);
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
        assertEquals(user.getId(), result.getOwnerId());
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
        user.setId(1);
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
        user.setId(1);
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
        Users owner = new Users(userEmail, "pass", "Ana", "Ionescu");
        owner.setId(1);
        firstList.setUser(owner);
        secondList.setUser(owner);

        when(shoppingListRepository.findAccessibleByEmail(userEmail)).thenReturn(List.of(firstList, secondList));

        List<ShoppingListDTO> result = shoppingListService.getUserLists(userEmail);

        assertEquals(2, result.size());
        assertEquals("Groceries", result.get(0).getTitle());
        assertEquals("Hardware", result.get(1).getTitle());
        assertTrue(result.stream().map(ShoppingListDTO::getId).toList().contains(secondList.getId()));
        assertEquals(1, result.get(0).getOwnerId());
    }

    @Test
    void getUserListsShouldReturnEmptyItemsWhenListHasNoCollection() {
        String userEmail = "ana@example.com";
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());
        list.setTitle("Groceries");
        list.setItems(null);
        Users owner = new Users(userEmail, "pass", "Ana", "Ionescu");
        owner.setId(1);
        list.setUser(owner);

        when(shoppingListRepository.findAccessibleByEmail(userEmail)).thenReturn(List.of(list));

        List<ShoppingListDTO> result = shoppingListService.getUserLists(userEmail);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getItems().isEmpty());
    }

    @Test
    void deleteListShouldRemoveOwnedList() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
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
        owner.setId(1);
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
        user.setId(1);
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
        owner.setId(1);
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
        user.setId(1);

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
        user.setId(1);

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
        user.setId(1);
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
        owner.setId(1);
        Users collaborator = new Users(collabEmail, "pass", "Collab", "User");
        collaborator.setId(2);
        
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
        owner.setId(1);
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
        owner.setId(1);
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
        owner.setId(1);
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
        owner.setId(1);
        Users collaborator = new Users(collabEmail, "pass", "Collab", "User");
        collaborator.setId(2);
        
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(owner);
        list.getCollaborators().add(collaborator);
        
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        
        ShoppingListDTO result = shoppingListService.getListById(listId, collabEmail);
        
        assertEquals(listId, result.getId());
    }

    @Test
    void finishShopping_shouldSetFinalStoreAndReturnDTO() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListDTO result = shoppingListService.finishShopping(listId, "Kaufland", userEmail);

        assertEquals("Kaufland", result.getFinalStore());
        verify(shoppingListRepository).save(list);
    }

    @Test
    void finishShopping_shouldTrimStoreName() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(shoppingListRepository.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListDTO result = shoppingListService.finishShopping(listId, "  Kaufland  ", userEmail);

        assertEquals("Kaufland", result.getFinalStore());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void finishShopping_shouldThrowWhenStoreNameIsInvalid(String storeName) {
        UUID listId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
            () -> shoppingListService.finishShopping(listId, storeName, "ana@example.com"));
    }

    @Test
    void markReceiptItemPurchased_shouldMarkMatchingItem() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName("Lapte");
        item.setChecked(false);
        list.getItems().add(item);

        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setGenericName("lapte");
        receiptItem.setSpecificName("Lapte Zuzu");
        receiptItem.setBrand("Zuzu");
        receiptItem.setPrice(new BigDecimal("10.50"));

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        org.mockito.Mockito.lenient().when(shoppingListRepository.save(any(ShoppingList.class))).thenReturn(list);
        org.mockito.Mockito.lenient().when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shoppingListService.markReceiptItemPurchased(listId, receiptItem, null, userEmail);

        assertTrue(item.isChecked());
        assertEquals("Zuzu", item.getBrand());
        assertEquals(new BigDecimal("10.50"), item.getPrice());
        verify(itemRepository).save(item);
    }

    @Test
    void markReceiptItemPurchased_shouldReturnEarlyWhenReceiptItemIsNull() {
        shoppingListService.markReceiptItemPurchased(UUID.randomUUID(), null, null, "ana@example.com");
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void markReceiptItemPurchased_shouldUpdateFromCatalogProduct() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName("Lapte");
        item.setChecked(false);
        list.getItems().add(item);

        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setGenericName("lapte");

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setGenericName("Lapte");
        catalogProduct.setSpecificName("Lapte Zuzu");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shoppingListService.markReceiptItemPurchased(listId, receiptItem, catalogProduct, userEmail);

        assertTrue(item.isChecked());
        assertEquals(catalogProduct, item.getCatalogItem());
    }

    @Test
    void markReceiptItemPurchased_shouldNotUpdateAlreadyCheckedItemFirst() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        Item checkedItem = new Item();
        checkedItem.setId(UUID.randomUUID());
        checkedItem.setName("Lapte");
        checkedItem.setChecked(true);

        Item uncheckedItem = new Item();
        uncheckedItem.setId(UUID.randomUUID());
        uncheckedItem.setName("Lapte");
        uncheckedItem.setChecked(false);

        list.getItems().add(checkedItem);
        list.getItems().add(uncheckedItem);

        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setGenericName("lapte");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shoppingListService.markReceiptItemPurchased(listId, receiptItem, null, userEmail);

        assertTrue(uncheckedItem.isChecked());
        verify(itemRepository).save(uncheckedItem);
    }

    @Test
    void markReceiptItemPurchased_shouldDoNothingWhenNoMatchFound() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName("Zahar");
        item.setChecked(false);
        list.getItems().add(item);

        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setGenericName("lapte"); // No match

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));

        shoppingListService.markReceiptItemPurchased(listId, receiptItem, null, userEmail);

        assertFalse(item.isChecked());
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void markReceiptItemPurchased_shouldDoNothingWhenBrandMismatches() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName("Lapte");
        item.setBrand("Zuzu");
        item.setChecked(false);
        list.getItems().add(item);

        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setGenericName("lapte");
        receiptItem.setBrand("Napolact"); // Mismatch

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));

        shoppingListService.markReceiptItemPurchased(listId, receiptItem, null, userEmail);

        assertFalse(item.isChecked());
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void markReceiptItemPurchased_shouldUpdateCategoryFromReceipt() {
        String userEmail = "ana@example.com";
        Users user = new Users(userEmail, "secret", "Ana", "Ionescu");
        user.setId(1);
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList();
        list.setId(listId);
        list.setUser(user);

        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName("Lapte");
        item.setChecked(false);
        item.setCategory(null);
        list.getItems().add(item);

        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setGenericName("lapte");
        receiptItem.setCategory("Dairy");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(list));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shoppingListService.markReceiptItemPurchased(listId, receiptItem, null, userEmail);

        assertEquals("Dairy", item.getCategory());
    }

    @Test
    void normalize_shouldReturnEmptyStringForNull() throws Exception {
        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("normalize", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(shoppingListService, (String) null);
        assertEquals("", result);
    }

    @Test
    void normalize_shouldTrimAndLowercase() throws Exception {
        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("normalize", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(shoppingListService, "  HELLO World  ");
        assertEquals("hello world", result);
    }

    @Test
    void firstNonBlank_shouldReturnPrimary() throws Exception {
        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("firstNonBlank", String.class, String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(shoppingListService, "primary", "fallback");
        assertEquals("primary", result);
    }

    @Test
    void firstNonBlank_shouldReturnFallbackWhenPrimaryBlank() throws Exception {
        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("firstNonBlank", String.class, String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(shoppingListService, "", "fallback");
        assertEquals("fallback", result);
    }

    @Test
    void firstNonBlank_shouldReturnNullWhenBothBlank() throws Exception {
        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("firstNonBlank", String.class, String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(shoppingListService, null, "");
        assertNull(result);
    }

    @Test
    void containsEither_shouldReturnTrueWhenLeftContainsRight() throws Exception {
        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("containsEither", String.class, String.class);
        method.setAccessible(true);
        Boolean result = (Boolean) method.invoke(shoppingListService, "hello world", "world");
        assertTrue(result);
    }

    @Test
    void containsEither_shouldReturnTrueWhenRightContainsLeft() throws Exception {
        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("containsEither", String.class, String.class);
        method.setAccessible(true);
        Boolean result = (Boolean) method.invoke(shoppingListService, "world", "hello world");
        assertTrue(result);
    }

    @Test
    void containsEither_shouldReturnFalseWhenNoMatch() throws Exception {
        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("containsEither", String.class, String.class);
        method.setAccessible(true);
        Boolean result = (Boolean) method.invoke(shoppingListService, "hello", "world");
        assertFalse(result);
    }

    @Test
    void mapToDTO_shouldHandleNullUser() throws Exception {
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID());
        list.setTitle("Test");
        list.setUser(null);

        java.lang.reflect.Method method = ShoppingListService.class.getDeclaredMethod("mapToDTO", ShoppingList.class);
        method.setAccessible(true);
        ShoppingListDTO result = (ShoppingListDTO) method.invoke(shoppingListService, list);

        assertNull(result.getOwnerId());
        assertNull(result.getOwnerEmail());
        assertNull(result.getOwnerName());
    }
}
