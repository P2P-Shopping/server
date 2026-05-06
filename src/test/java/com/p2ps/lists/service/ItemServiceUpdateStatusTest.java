package com.p2ps.lists.service;

import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.auth.model.Users;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.model.UserProductHistory;
import com.p2ps.lists.repo.ItemRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceUpdateStatusTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ShoppingListRepository shoppingListRepository;

    @Mock
    private UserProductHistoryRepository historyRepository;

    @Mock
    private ProductCatalogRepository catalogRepository;

    @Mock
    private CatalogService catalogService;

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
    void updateItemStatusShouldNotSaveHistoryWhenUnchecked() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();
        item.setChecked(true);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItemStatus(itemId, false, 123L);

        assertEquals(false, result.isChecked());
        verify(historyRepository, never())
                .findByUser_IdAndCustomNameIgnoreCase(any(), any());
        verify(catalogRepository, never()).searchByKeywordFuzzy(any());
        verify(catalogService, never()).recordPurchase(any(), any(), any(), any(), any());
    }

    @Test
    void updateItemStatusShouldReuseCatalogMatchWhenFoundAndSameBrand() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();
        item.setName("Milk");
        item.setBrand("Brand A"); // Same brand as catalog
        Users user = item.getShoppingList().getUser();
        user.setId(10);

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setSpecificName("Milk 1L");
        catalogProduct.setBrand("Brand A"); // Match brand

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(10, "Milk")).thenReturn(null);
        when(catalogRepository.searchByKeywordFuzzy("Milk")).thenReturn(java.util.List.of(catalogProduct));

        itemService.updateItemStatus(itemId, true, 123L);

        verify(catalogRepository).searchByKeywordFuzzy("Milk");
        verify(catalogService, never()).recordPurchase(any(), any(), any(), any(), any());
        verify(historyRepository).save(any(UserProductHistory.class));
    }

    @Test
    void updateItemStatusShouldCreateCatalogEntryWhenNoMatchExists() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();
        item.setName("Cheese");
        item.setBrand("Local");
        item.setPrice(new BigDecimal("12.50"));
        item.setCategory(" ");
        Users user = item.getShoppingList().getUser();
        user.setId(11);

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setSpecificName("Cheese");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(11, "Cheese")).thenReturn(null);
        when(catalogRepository.searchByKeywordFuzzy("Cheese")).thenReturn(null);
        when(catalogService.recordPurchase("Cheese", "Cheese", "Local", "Altele", new BigDecimal("12.50")))
                .thenReturn(catalogProduct);

        itemService.updateItemStatus(itemId, true, 123L);

        verify(catalogService).recordPurchase(
                "Cheese",
                "Cheese",
                "Local",
                "Altele",
                new BigDecimal("12.50")
        );
        verify(historyRepository).save(any(UserProductHistory.class));
    }

    @Test
    void updateItemStatusShouldCreateCatalogEntryWhenMatchExistsButDifferentBrand() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();
        item.setName("Milk");
        item.setBrand("Different Brand"); // Different brand than what the catalog has
        item.setPrice(new BigDecimal("5.00"));
        item.setCategory("Dairy");
        Users user = item.getShoppingList().getUser();
        user.setId(12);

        // This is what the fuzzy search finds, but the brand is different
        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setSpecificName("Milk 1L");
        catalogProduct.setBrand("Brand A");

        // The newly created catalog product
        ProductCatalog newCatalogProduct = new ProductCatalog();
        newCatalogProduct.setId(UUID.randomUUID());
        newCatalogProduct.setSpecificName("Milk");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(12, "Milk")).thenReturn(null);
        when(catalogRepository.searchByKeywordFuzzy("Milk")).thenReturn(java.util.List.of(catalogProduct));
        when(catalogService.recordPurchase("Milk", "Milk", "Different Brand", "Dairy", new BigDecimal("5.00")))
                .thenReturn(newCatalogProduct);

        itemService.updateItemStatus(itemId, true, 123L);

        verify(catalogService).recordPurchase(
                "Milk",
                "Milk",
                "Different Brand",
                "Dairy",
                new BigDecimal("5.00")
        );
        verify(historyRepository).save(any(UserProductHistory.class));
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