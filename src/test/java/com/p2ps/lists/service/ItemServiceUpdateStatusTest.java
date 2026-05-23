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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

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

    @Mock
    private com.p2ps.telemetry.repository.TelemetryRepository telemetryRepository;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
        verify(catalogRepository, never()).searchByKeywordStrict(any());
        verify(catalogService, never()).recordPurchase(any(), any(), any(), any(), any());
    }

    @Test
    void updateItemStatusShouldReuseCatalogMatchWhenFound() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();
        item.setName("Milk");
        Users user = item.getShoppingList().getUser();
        user.setId(10);

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setSpecificName("Milk 1L");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(10, "Milk")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Milk")).thenReturn(java.util.List.of(catalogProduct));

        itemService.updateItemStatus(itemId, true, 123L);

        verify(catalogRepository).searchByKeywordStrict("Milk");
        verify(catalogService, never()).recordPurchase(any(), any(), any(), any(), any());
        verify(historyRepository).save(any(UserProductHistory.class));
    }

    @Test
    void updateItemStatusShouldSaveOnlyToHistoryWhenNoCatalogMatchExists() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();
        item.setName("Cheese");
        item.setBrand("Local");
        item.setPrice(new BigDecimal("12.50"));
        item.setCategory(" ");
        Users user = item.getShoppingList().getUser();
        user.setId(11);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(11, "Cheese")).thenReturn(null);
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(11, "Cheese Local")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Cheese Local")).thenReturn(java.util.List.of());

        itemService.updateItemStatus(itemId, true, 123L);

        verify(catalogService, never()).recordPurchase(any(), any(), any(), any(), any());
        verify(catalogRepository, never()).save(any());
        verify(historyRepository).save(any(UserProductHistory.class));
    }

    @Test
    void updateItemStatusShouldThrowWhenItemDoesNotExist() {
        UUID itemId = UUID.randomUUID();

        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class,
                () -> itemService.updateItemStatus(itemId, true, 123L));
    }

    @Test
    void updateItemStatus_whenUncheckedToChecked_savesToHistory() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();
        item.setName("Milk");
        item.setChecked(false); // Unchecked
        Users user = item.getShoppingList().getUser();
        user.setId(10);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.updateItemStatus(itemId, true, 123L);

        verify(historyRepository, atLeastOnce()).findByUser_IdAndCustomNameIgnoreCase(any(), eq("Milk"));
    }

    @Test
    void updateItemStatus_whenAlreadyChecked_doesNotSaveDuplicateHistory() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItem();
        item.setName("Milk");
        item.setChecked(true); // Already checked
        Users user = item.getShoppingList().getUser();
        user.setId(10);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.updateItemStatus(itemId, true, 123L);

        verify(historyRepository, never()).findByUser_IdAndCustomNameIgnoreCase(any(), any());
    }

    private Item buildItem() {
        Users user = new Users("ana@example.com", "secret", "Ana", "Ionescu");
        user.setId(10); // set ID to avoid NPE in search hierarchy
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
