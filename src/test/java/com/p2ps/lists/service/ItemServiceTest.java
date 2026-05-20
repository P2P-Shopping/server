package com.p2ps.lists.service;

import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.service.AiService;
import com.p2ps.dto.ActionType;
import com.p2ps.dto.ListUpdatePayload;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.catalog.service.CatalogService;
import com.p2ps.catalog.service.StorePriceService;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListValidationException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.model.UserProductHistory;
import com.p2ps.lists.repo.ItemRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import com.p2ps.lists.repo.UserProductHistoryRepository;
import com.p2ps.auth.model.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

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
    private AiService aiService;

    @Mock
    private StorePriceService storePriceService;

    @InjectMocks
    private ItemService itemService;

    private UUID listId;
    private UUID itemId;
    private String userEmail;
    private ShoppingList mockList;
    private Item mockItem;
    private Users mockUser;

    @BeforeEach
    void setUp() {
        listId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        userEmail = "test@user.com";

        mockUser = new Users();
        mockUser.setId(1);
        mockUser.setEmail(userEmail);

        mockList = new ShoppingList();
        mockList.setId(listId);
        mockList.setUser(mockUser);

        mockItem = new Item();
        mockItem.setId(itemId);
        mockItem.setShoppingList(mockList);
        mockItem.setName("Old Item");
        mockItem.setChecked(false);

        ReflectionTestUtils.setField(itemService, "self", itemService);
    }

    // ==========================================
    // TIER 1: NEW ARCHITECTURE TESTS (AI & Catalog Protection)
    // ==========================================

    @Test
    void givenReceiptWithJunkItems_whenProcessed_thenAiFiltersJunkAndRefinesValidItems() {
        ItemRequest validReq = new ItemRequest();
        validReq.setName("lapte 1000g");
        validReq.setPrice(new BigDecimal("5.0"));

        ItemRequest junkReq = new ItemRequest();
        junkReq.setName("Garantie TV");
        junkReq.setPrice(new BigDecimal("100.0"));

        List<ItemRequest> requests = List.of(validReq, junkReq);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(anyInt(), anyString())).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(eq(listId), anyString())).thenReturn(List.of());

        // MOCK AI SERVICE to return ONLY the filtered/refined item
        when(aiService.postValidateAndFilterReceiptItems(anyList())).thenAnswer(invocation -> {
            List<ItemDTO> dtos = invocation.getArgument(0);
            ItemDTO validDto = dtos.stream().filter(d -> d.getName().equals("lapte 1000g")).findFirst().get();
            validDto.setName("Lapte");
            validDto.setQuantity("1kg");
            return List.of(validDto); // Dropped "Garantie TV"
        });

        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ItemDTO> result = itemService.addItemsToList(listId, requests, userEmail);

        assertThat(result).hasSize(1);
        ItemDTO savedItem = result.get(0);
        assertThat(savedItem.getName()).isEqualTo("Lapte");
        assertThat(savedItem.getQuantity()).isEqualTo("1kg");
        assertThat(savedItem.getPrice()).isEqualTo(new BigDecimal("5.0"));

        ArgumentCaptor<List<Item>> listCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(itemRepository).saveAll(listCaptor.capture());

        List<Item> savedEntities = listCaptor.getValue();
        assertThat(savedEntities).hasSize(1);
        assertThat(savedEntities.get(0).getName()).isEqualTo("Lapte");
    }

    @Test
    void givenAiServiceFails_whenProcessed_thenFallbackToOriginalMappedItems() {
        ItemRequest req = new ItemRequest();
        req.setName("Garantie TV");
        List<ItemRequest> requests = List.of(req);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(anyInt(), anyString())).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(eq(listId), anyString())).thenReturn(List.of());

        when(aiService.postValidateAndFilterReceiptItems(anyList())).thenThrow(new RuntimeException("LLM Timeout"));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ItemDTO> result = itemService.addItemsToList(listId, requests, userEmail);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Garantie TV");
    }

    @Test
    void givenUnknownProduct_whenAdded_thenSavedOnlyToItemAndHistoryWithNullCatalogId() {
        ItemRequest req = new ItemRequest();
        req.setName("Unknown Magic Fruit");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Unknown Magic Fruit")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Unknown Magic Fruit")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Unknown Magic Fruit")).thenReturn(List.of());

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getName()).isEqualTo("Unknown Magic Fruit");

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCatalogItem()).isNull();

        ArgumentCaptor<UserProductHistory> historyCaptor = ArgumentCaptor.forClass(UserProductHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getCatalogItem()).isNull();

        verify(catalogRepository, never()).save(any());
        verify(catalogService, never()).recordPurchase(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void recordReceiptItem_shouldIgnoreJunkItems() {
        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setSpecificName("Garantie Extinsa TV");
        receiptItem.setPrice(new BigDecimal("99.99"));

        ItemService.ReceiptProcessingResult result = itemService.recordReceiptItem(receiptItem, "Kaufland", mockUser);

        assertThat(result.ignored()).isTrue();
        assertThat(result.catalogMatch()).isNull();
        verify(historyRepository, never()).save(any());
        verify(catalogRepository, never()).findById(any());
    }

    @Test
    void recordReceiptItem_shouldUseCatalogIdAndSaveOnlyToHistory() {
        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setGenericName("Lapte");
        receiptItem.setSpecificName("Lapte Zuzu");
        receiptItem.setCatalogId(UUID.randomUUID().toString());
        receiptItem.setPrice(new BigDecimal("8.99"));

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.fromString(receiptItem.getCatalogId()));

        when(catalogRepository.findById(catalogProduct.getId())).thenReturn(Optional.of(catalogProduct));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Lapte Zuzu")).thenReturn(null);

        ItemService.ReceiptProcessingResult result = itemService.recordReceiptItem(receiptItem, "Kaufland", mockUser);

        assertThat(result.ignored()).isFalse();
        assertThat(result.catalogMatch()).isEqualTo(catalogProduct);
        verify(historyRepository).save(any(UserProductHistory.class));
        verify(catalogRepository, never()).save(any());
        verify(catalogService, never()).recordPurchase(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void recordReceiptItem_shouldIgnoreNullInput() {
        ItemService.ReceiptProcessingResult result = itemService.recordReceiptItem(null, "Kaufland", mockUser);

        assertThat(result.ignored()).isTrue();
        assertThat(result.catalogMatch()).isNull();
        verifyNoInteractions(storePriceService);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void recordReceiptItem_shouldSkipNegativeReceiptPrice() {
        ParsedItemResponse receiptItem = new ParsedItemResponse();
        receiptItem.setSpecificName("Lapte Zuzu");
        receiptItem.setPrice(new BigDecimal("-8.99"));

        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Lapte Zuzu")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Lapte Zuzu")).thenReturn(List.of());

        ItemService.ReceiptProcessingResult result = itemService.recordReceiptItem(receiptItem, "Kaufland", mockUser);

        assertThat(result.ignored()).isFalse();
        verify(storePriceService, never()).recordStorePrice(any(), anyString(), any());
        ArgumentCaptor<UserProductHistory> historyCaptor = ArgumentCaptor.forClass(UserProductHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPrice()).isNull();
    }

    @Test
    void givenKnownProduct_whenAdded_thenMappedToExistingCatalogId() {
        ItemRequest req = new ItemRequest();
        req.setName("Milk");

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setGenericName("Official Milk");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Milk")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Milk")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogProduct.getId())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Milk")).thenReturn(List.of());

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getName()).isEqualTo("Milk");

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCatalogItem()).isEqualTo(catalogProduct);

        verify(catalogRepository, never()).save(any());
    }

    @Test
    void givenReceiptItemAlreadyOnList_whenProcessed_thenExistingItemIsUpdatedWithPriceAndNoDuplicateCreated() {
        ItemRequest req = new ItemRequest();
        req.setName("Juice");
        req.setBrand("Natural");
        req.setPrice(new BigDecimal("10.0"));
        req.setQuantity("1L");

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setGenericName("Juice");
        catalogProduct.setBrand("Natural");

        Item existingItem = new Item();
        existingItem.setId(UUID.randomUUID());
        existingItem.setName("Juice");
        existingItem.setCatalogItem(catalogProduct);
        existingItem.setQuantity("2L");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Juice Natural")).thenReturn(null);
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Juice")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Juice Natural")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogProduct.getId())).thenReturn(List.of(existingItem));

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getId()).isEqualTo(existingItem.getId());
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("10.0"));

        verify(itemRepository).save(existingItem);
        verify(itemRepository, never()).saveAll(anyList());
    }

    @Test
    void givenItemWithSameNameButDifferentBrand_whenAdded_thenDoNotMergeAndCreateNewItem() {
        ItemRequest req = new ItemRequest();
        req.setName("Lapte");
        req.setBrand("Danone");

        Item existingItem = new Item();
        existingItem.setId(UUID.randomUUID());
        existingItem.setName("Lapte");
        existingItem.setBrand(null); // Different brand

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Lapte Danone")).thenReturn(null);
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Lapte")).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict("Lapte Danone")).thenReturn(List.of());

        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Lapte")).thenReturn(List.of(existingItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getId()).isNotEqualTo(existingItem.getId());
        assertThat(result.getName()).isEqualTo("Lapte");
        assertThat(result.getBrand()).isEqualTo("Danone");

        verify(itemRepository).save(any(Item.class));
        verify(itemRepository, never()).delete(existingItem);
    }

    // ==========================================
    // TIER 2: MERGED TESTS FROM MAIN (External Item IDs)
    // ==========================================

    @Test
    void addItemToList_AttachesExternalItemId_WhenRoutableMatchExists() {
        ItemRequest req = new ItemRequest();
        req.setName("item_123");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(anyInt(), anyString())).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());

        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "item_123")).thenReturn(List.of());
        when(itemRepository.findRoutableExternalItemIdByName("item_123")).thenReturn(Optional.of("item_123"));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getExternalItemId()).isEqualTo("item_123");
        verify(itemRepository).save(argThat(item -> "item_123".equals(item.getExternalItemId())));
    }

    @Test
    void addItemToList_DoesNotOverwriteExistingExternalItemId() {
        ItemRequest req = new ItemRequest();
        req.setName("Existing External Item");

        Item existingItem = new Item();
        existingItem.setId(UUID.randomUUID());
        existingItem.setName("Existing External Item");
        existingItem.setExternalItemId("pre-existing-external-id");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(anyInt(), anyString())).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());

        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Existing External Item"))
                .thenReturn(List.of(existingItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getExternalItemId()).isEqualTo("pre-existing-external-id");
        verify(itemRepository, never()).findRoutableExternalItemIdByName(anyString());
    }

    @Test
    void addItemToList_DoesNotAttachExternalItemId_WhenNameIsBlank() {
        ItemRequest req = new ItemRequest();
        req.setName("   ");

        assertThatThrownBy(() -> itemService.addItemToList(listId, req, userEmail))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("Item name cannot be empty");
    }

    @Test
    void createAndSaveNewItem_AttachesExternalItemId_WhenRoutableMatchExists() {
        ItemRequest req = new ItemRequest();
        req.setName("routable-item");
        req.setPrice(BigDecimal.ZERO);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(anyInt(), anyString())).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());

        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "routable-item")).thenReturn(List.of());
        when(itemRepository.findRoutableExternalItemIdByName("routable-item")).thenReturn(Optional.of("routable-external-id"));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getExternalItemId()).isEqualTo("routable-external-id");
        verify(itemRepository).save(argThat(item -> "routable-external-id".equals(item.getExternalItemId())));
    }

    @Test
    void updateItem_AttachesExternalItemId_WhenRoutableMatchExists() {
        ItemRequest req = new ItemRequest();
        req.setName("update-routable-item");

        Item itemToUpdate = new Item();
        itemToUpdate.setId(itemId);
        itemToUpdate.setName("Old Name");
        itemToUpdate.setShoppingList(mockList);
        itemToUpdate.setExternalItemId(null);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(itemToUpdate));
        when(itemRepository.findRoutableExternalItemIdByName("update-routable-item")).thenReturn(Optional.of("update-external-id"));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItem(itemId, req, userEmail);

        assertThat(result.getExternalItemId()).isEqualTo("update-external-id");
        verify(itemRepository).save(argThat(item -> "update-external-id".equals(item.getExternalItemId())));
    }

    @Test
    void updateItem_DoesNotOverwriteExistingExternalItemId() {
        ItemRequest req = new ItemRequest();
        req.setName("Updated Name");

        Item itemToUpdate = new Item();
        itemToUpdate.setId(itemId);
        itemToUpdate.setName("Old Name");
        itemToUpdate.setShoppingList(mockList);
        itemToUpdate.setExternalItemId("existing-external-id");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(itemToUpdate));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItem(itemId, req, userEmail);

        assertThat(result.getExternalItemId()).isEqualTo("existing-external-id");
        verify(itemRepository, never()).findRoutableExternalItemIdByName(anyString());
    }

    @Test
    void addItemsToList_AttachesExternalItemId_ToNewItems() {
        ItemRequest req = new ItemRequest();
        req.setName("batch-routable-item");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(anyInt(), anyString())).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());

        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "batch-routable-item")).thenReturn(List.of());
        when(itemRepository.findRoutableExternalItemIdByName("batch-routable-item")).thenReturn(Optional.of("batch-external-id"));
        when(aiService.postValidateAndFilterReceiptItems(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ItemDTO> result = itemService.addItemsToList(listId, List.of(req), userEmail);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExternalItemId()).isEqualTo("batch-external-id");
    }

    @Test
    void addItemsToList_AttachesExternalItemId_ToMergedDbItems() {
        ItemRequest req = new ItemRequest();
        req.setName("merge-routable-item");
        req.setQuantity("2 buc");

        Item dbItem = new Item();
        dbItem.setId(UUID.randomUUID());
        dbItem.setName("merge-routable-item");
        dbItem.setQuantity("1 buc");
        dbItem.setExternalItemId(null);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(anyInt(), anyString())).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());

        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "merge-routable-item")).thenReturn(List.of(dbItem));
        when(itemRepository.findRoutableExternalItemIdByName("merge-routable-item")).thenReturn(Optional.of("merge-external-id"));
        when(aiService.postValidateAndFilterReceiptItems(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ItemDTO> result = itemService.addItemsToList(listId, List.of(req), userEmail);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExternalItemId()).isEqualTo("merge-external-id");
    }

    @Test
    void mapToDTO_IncludesExternalItemId() throws Exception {
        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName("Test Item");
        item.setChecked(true);
        item.setBrand("Test Brand");
        item.setQuantity("5");
        item.setPrice(BigDecimal.TEN);
        item.setCategory("Test Category");
        item.setRecurrent(true);
        item.setLastUpdatedTimestamp(12345L);
        item.setCreatedAt(10000L);
        item.setExternalItemId("map-to-dto-external-id");

        java.lang.reflect.Method mapToDtoMethod = ItemService.class.getDeclaredMethod("mapToDTO", Item.class);
        mapToDtoMethod.setAccessible(true);
        ItemDTO result = (ItemDTO) mapToDtoMethod.invoke(itemService, item);

        assertThat(result.getExternalItemId()).isEqualTo("map-to-dto-external-id");
    }

    // ==========================================
    // TIER 3: STANDARD TESTS (Validations, Exceptions, etc.)
    // ==========================================

    @Test
    void addItemToList_Success() {
        ItemRequest req = new ItemRequest();
        req.setName("Milk");
        req.setPrice(BigDecimal.TEN);
        req.setIsRecurrent(true);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Milk")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Milk")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, req.getName())).thenReturn(List.of());
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
    void addItemsToList_ReturnsEmptyList_WhenRequestIsEmpty() {
        List<ItemDTO> result = itemService.addItemsToList(listId, List.of(), userEmail);
        assertThat(result).isEmpty();
        verifyNoInteractions(itemRepository, historyRepository, catalogRepository, catalogService);
    }

    @Test
    void addItemsToList_Success() {
        ItemRequest req1 = new ItemRequest(); req1.setName("Item 1");
        ItemRequest req2 = new ItemRequest(); req2.setName("Item 2");
        List<ItemRequest> requests = List.of(req1, req2);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(eq(listId), anyString())).thenReturn(List.of());

        when(aiService.postValidateAndFilterReceiptItems(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemsToList(listId, requests, userEmail);

        verify(itemRepository).saveAll(anyList());
    }

    @Test
    void addItemsToList_ThrowsAccessDenied_WhenWrongUser() {
        ItemRequest req = new ItemRequest();
        req.setName("Milk");
        List<ItemRequest> requests = List.of(req);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));

        assertThatThrownBy(() -> itemService.addItemsToList(listId, requests, "hacker@user.com"))
                .isInstanceOf(ListAccessDeniedException.class)
                .hasMessageContaining("You do not have permission to add items to this list");

        verify(itemRepository, never()).saveAll(anyList());
        verifyNoInteractions(historyRepository, catalogRepository, catalogService);
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

    @Test
    void addItemToList_MergesHistoricalDuplicates_AndMaintainsPrecision() {
        ItemRequest req = new ItemRequest();
        req.setName("Flour");
        req.setQuantity("2.2 kg");

        Item duplicate1 = new Item();
        duplicate1.setId(UUID.randomUUID());
        duplicate1.setName("Flour");
        duplicate1.setQuantity("1.1 kg");

        Item duplicate2 = new Item();
        duplicate2.setId(UUID.randomUUID());
        duplicate2.setName("Flour");
        duplicate2.setQuantity("1 kg");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Flour")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Flour")).thenReturn(List.of());

        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Flour"))
                .thenReturn(List.of(duplicate1, duplicate2));

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        verify(itemRepository, times(1)).delete(duplicate2);
        verify(itemRepository).save(duplicate1);
    }

    @Test
    void addItemToList_PreservesMetadataInMerge() {
        ItemRequest req = new ItemRequest();
        req.setName("Flour");
        req.setCategory("Baking");
        req.setIsRecurrent(true);

        Item existing = new Item();
        existing.setId(UUID.randomUUID());
        existing.setName("Flour");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Flour")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Flour")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Flour"))
                .thenReturn(List.of(existing));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void addItemsToList_MergesDuplicatesWithinSameBatch() {
        ItemRequest req1 = new ItemRequest();
        req1.setName("Eggs");
        req1.setQuantity("2 buc");

        ItemRequest req2 = new ItemRequest();
        req2.setName("Milk");
        req2.setQuantity("1 l");

        ItemRequest req3 = new ItemRequest();
        req3.setName("eggs"); // Different case
        req3.setQuantity("4 buc");

        List<ItemRequest> requests = List.of(req1, req2, req3);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(anyInt(), anyString())).thenReturn(null);
        lenient().when(catalogRepository.searchByKeywordStrict(anyString())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(any(UUID.class), anyString()))
                .thenReturn(List.of());

        when(aiService.postValidateAndFilterReceiptItems(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemsToList(listId, requests, userEmail);

        verify(itemRepository).saveAll(anyList());
    }

    @Test
    void updateItem_UpdatesAllOptionalFields() {
        ItemRequest req = new ItemRequest();
        req.setName("Updated Milk");
        req.setBrand("Zuzu");
        req.setQuantity("2 litri");
        req.setPrice(new BigDecimal("15.5"));
        req.setCategory("Dairy");
        req.setIsRecurrent(true);
        req.setIsChecked(true);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItem(itemId, req, userEmail);

        assertThat(result.getBrand()).isEqualTo("Zuzu");
        assertThat(result.getQuantity()).isEqualTo("2 litri");
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("15.5"));
        assertThat(result.getCategory()).isEqualTo("Dairy");
        assertThat(result.isRecurrent()).isTrue();
        assertThat(result.isChecked()).isTrue();
    }

    @Test
    void updateItemFromSync_Success_WithJsonContent() {
        com.p2ps.dto.ListUpdatePayload payload = new com.p2ps.dto.ListUpdatePayload();
        payload.setAction(com.p2ps.dto.ActionType.UPDATE);
        payload.setChecked(true);
        payload.setContent("{\"name\":\"Synced Name\",\"brand\":\"Synced Brand\"}");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItemFromSync(itemId, payload);

        assertThat(result.isChecked()).isTrue();
        assertThat(result.getName()).isEqualTo("Synced Name");
        assertThat(result.getBrand()).isEqualTo("Synced Brand");
        verify(itemRepository).save(mockItem);
    }

    @Test
    void updateItemFromSync_Fallback_WithPlainContent() {
        com.p2ps.dto.ListUpdatePayload payload = new com.p2ps.dto.ListUpdatePayload();
        payload.setAction(com.p2ps.dto.ActionType.UPDATE);
        payload.setContent("Simple Name Fallback");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItemFromSync(itemId, payload);

        assertThat(result.getName()).isEqualTo("Simple Name Fallback");
        verify(itemRepository).save(mockItem);
    }

    @Test
    void updateItemFromSync_Success_WithAllFields() {
        com.p2ps.dto.ListUpdatePayload payload = new com.p2ps.dto.ListUpdatePayload();
        payload.setAction(com.p2ps.dto.ActionType.UPDATE);
        payload.setContent("{" +
                "\"name\":\"Full Update\"," +
                "\"brand\":\"Brand X\"," +
                "\"quantity\":\"5\"," +
                "\"price\":10.5," +
                "\"category\":\"Food\"" +
                "}");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItemFromSync(itemId, payload);

        assertThat(result.getName()).isEqualTo("Full Update");
        assertThat(result.getBrand()).isEqualTo("Brand X");
        assertThat(result.getQuantity()).isEqualTo("5");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("10.5"));
        assertThat(result.getCategory()).isEqualTo("Food");
    }

    @Test
    void updateItemFromSync_ThrowsValidationException_WhenNameIsBlank() {
        com.p2ps.dto.ListUpdatePayload payload = new com.p2ps.dto.ListUpdatePayload();
        payload.setAction(com.p2ps.dto.ActionType.UPDATE);
        payload.setContent("{\"name\":\"  \"}");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> itemService.updateItemFromSync(itemId, payload))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("Item name cannot be empty");
    }

    @Test
    void updateItemFromSync_ThrowsValidationException_WhenPriceIsNegative() {
        com.p2ps.dto.ListUpdatePayload payload = new com.p2ps.dto.ListUpdatePayload();
        payload.setAction(com.p2ps.dto.ActionType.UPDATE);
        payload.setContent("{\"price\":-10}");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> itemService.updateItemFromSync(itemId, payload))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("Price must be zero or positive");
    }

    @Test
    void addItemToList_WithNullRecurrent_DefaultsToFalse() {
        ItemRequest req = new ItemRequest();
        req.setName("Milk");
        req.setIsRecurrent(null);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Milk")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Milk")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, req.getName())).thenReturn(List.of());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.isRecurrent()).isFalse();
    }

    @Test
    void updateItem_WithNullFields_DoesNotChangeThem() {
        ItemRequest req = new ItemRequest();
        // All fields null

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItem(itemId, req, userEmail);

        assertThat(result.getName()).isEqualTo("Old Item");
        verify(itemRepository).save(mockItem);
    }

    @Test
    void addItemsToList_MergesWithExistingDbItems_AndCleansHistoricalDuplicates() {
        ItemRequest req = new ItemRequest();
        req.setName("Zahar");
        req.setQuantity("1 kg");

        Item dbDuplicate1 = new Item();
        dbDuplicate1.setId(UUID.randomUUID());
        dbDuplicate1.setName("Zahar");
        dbDuplicate1.setQuantity("2 kg");

        Item dbDuplicate2 = new Item();
        dbDuplicate2.setId(UUID.randomUUID());
        dbDuplicate2.setName("Zahar");
        dbDuplicate2.setQuantity("0.5 kg");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Zahar")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Zahar")).thenReturn(List.of());

        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Zahar"))
                .thenReturn(List.of(dbDuplicate1, dbDuplicate2));

        lenient().when(aiService.postValidateAndFilterReceiptItems(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemsToList(listId, List.of(req), userEmail);

        verify(itemRepository).delete(dbDuplicate2);
    }

    @Test
    void addItemToList_ReturnsNewQuantity_WhenOldQuantityIsBlank() {
        ItemRequest req = new ItemRequest();
        req.setName("Apa");
        req.setQuantity("2 buc"); // Your fix
        Item existingItem = new Item();
        existingItem.setId(UUID.randomUUID());
        existingItem.setName("Apa");
        existingItem.setQuantity("   ");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Apa")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Apa")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Apa"))
                .thenReturn(List.of(existingItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getQuantity()).isEqualTo("2 buc"); // Your fix
    }

    @Test
    void addItemsToListWithRetry_SuccessOnFirstTry() {
        ItemRequest req = new ItemRequest();
        req.setName("Item");
        List<ItemRequest> requests = List.of(req);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Item")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Item")).thenReturn(List.of());

        when(aiService.postValidateAndFilterReceiptItems(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        when(itemRepository.saveAll(anyList())).thenReturn(List.of(new Item()));

        itemService.addItemsToListWithRetry(listId, requests, userEmail);
        verify(itemRepository).saveAll(anyList());
    }

    @Test
    void addItemToList_RetriesOnDataIntegrityViolation() {
        ItemRequest req = new ItemRequest();
        req.setName("RaceConditionItem");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "RaceConditionItem")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("RaceConditionItem")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "RaceConditionItem"))
                .thenReturn(List.of()); // First call: not found

        // Mock save to throw exception on first call, then succeed on second call (after retry)
        when(itemRepository.save(any(Item.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);
        verify(itemRepository, times(2)).save(any(Item.class));
    }

    @Test
    void addItemsToListWithRetry_ThrowsExceptionAfterRetry() {
        ItemRequest req = new ItemRequest();
        req.setName("RetryFailItem");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "RetryFailItem")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("RetryFailItem")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "RetryFailItem"))
                .thenReturn(List.of());

        when(aiService.postValidateAndFilterReceiptItems(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Always throw exception
        when(itemRepository.saveAll(anyList()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate"));

        assertThatThrownBy(() -> itemService.addItemsToListWithRetry(listId, List.of(req), userEmail))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ==========================================
    // SMART QUANTITY PARSING TESTS (Noul Comportament)
    // ==========================================

    @Test
    void addItemToList_SumsCompatibleQuantitiesCorrectly() {
        ItemRequest req = new ItemRequest();
        req.setName("Faina");
        req.setQuantity("500 g");

        Item existingItem = new Item();
        existingItem.setId(UUID.randomUUID());
        existingItem.setName("Faina");
        existingItem.setQuantity("1.2 kg");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Faina")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Faina")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Faina"))
                .thenReturn(List.of(existingItem));

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        // Smart summation: 1.2 kg + 500 g = 1.7 kg
        assertThat(result.getQuantity()).isEqualTo("1.7 kg");
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void addItemToList_SumsGramsAndKilogramsIntoSingleItem() {
        ItemRequest req = new ItemRequest();
        req.setName("Zahar");
        req.setQuantity("1 kg");

        Item existingItem = new Item();
        existingItem.setId(UUID.randomUUID());
        existingItem.setName("Zahar");
        existingItem.setQuantity("200 g");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Zahar")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Zahar")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Zahar"))
                .thenReturn(List.of(existingItem));

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getQuantity()).isEqualTo("1.2 kg");
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void addItemToList_ReplacesQuantityWhenUnitsAreIncompatible() {
        ItemRequest req = new ItemRequest();
        req.setName("Mere");
        req.setQuantity("3 buc");

        Item existingItem = new Item();
        existingItem.setId(UUID.randomUUID());
        existingItem.setName("Mere");
        existingItem.setQuantity("2 kg");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Mere")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Mere")).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Mere"))
                .thenReturn(List.of(existingItem));

        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.addItemToList(listId, req, userEmail);

        assertThat(result.getQuantity()).isEqualTo("3 buc");
    }

    @ParameterizedTest
    @CsvSource({
            "-2 kg, positive number",
            "9999999 kg, maximum accepted limit",
            "'un pic de lapte te rog', Quantity format is NOT valid"
    })
    void addItemToList_ThrowsValidationException_WhenQuantityIsInvalid(String quantity, String expectedMessage) {
        ItemRequest req = new ItemRequest();
        req.setName("Rosii");
        req.setQuantity(quantity);

        assertThatThrownBy(() -> itemService.addItemToList(listId, req, userEmail))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining(expectedMessage);

        verify(itemRepository, never()).save(any(Item.class));
    }

    // ==========================================
    // TIER 4: NEW COVERAGE TESTS (isBrandMatch, history variants, exceptions)
    // ==========================================

    @Test
    void resolveCatalogMatch_BrandMatch_UserProvided_CatalogMatches() {
        ItemRequest req = new ItemRequest();
        req.setName("Iaurt");
        req.setBrand("Danone");

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setGenericName("Iaurt");
        catalogProduct.setBrand("Danone"); // Matching brand

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Iaurt Danone")).thenReturn(null);
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Iaurt")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Iaurt Danone")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogProduct.getId())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Iaurt")).thenReturn(List.of());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCatalogItem()).isEqualTo(catalogProduct);
    }

    @Test
    void resolveCatalogMatch_BrandMatch_UserProvided_CatalogDoesNotMatch() {
        ItemRequest req = new ItemRequest();
        req.setName("Iaurt");
        req.setBrand("Danone");

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setGenericName("Iaurt");
        catalogProduct.setBrand("Zuzu"); // Mismatching brand

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Iaurt Danone")).thenReturn(null);
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Iaurt")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Iaurt Danone")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Iaurt")).thenReturn(List.of());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        // Since brand didn't match, catalog item is null
        assertThat(itemCaptor.getValue().getCatalogItem()).isNull();
    }

    @Test
    void resolveCatalogMatch_BrandMatch_UserProvided_CatalogNoBrandButContainsInName() {
        ItemRequest req = new ItemRequest();
        req.setName("Iaurt");
        req.setBrand("Danone");

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setSpecificName("Iaurt Danone cu capsuni");
        catalogProduct.setBrand(null); // No explicit brand, but it's in the specific name

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Iaurt Danone")).thenReturn(null);
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Iaurt")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Iaurt Danone")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogProduct.getId())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Iaurt")).thenReturn(List.of());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        // Brand matched in the specificName, so it linked correctly
        assertThat(itemCaptor.getValue().getCatalogItem()).isEqualTo(catalogProduct);
    }

    @Test
    void resolveCatalogMatch_UserNotProvided_CatalogHasBrandButGenericNameMatches() {
        ItemRequest req = new ItemRequest();
        req.setName("Iaurt");
        req.setBrand(null);

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setGenericName("Iaurt");
        catalogProduct.setBrand("Danone");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Iaurt")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Iaurt")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogProduct.getId())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Iaurt")).thenReturn(List.of());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCatalogItem()).isEqualTo(catalogProduct);
    }

   

    @Test
    void resolveCatalogMatch_BrandMatch_UserNotProvided_CatalogHasBrandAndUserTypedItInName() {
        ItemRequest req = new ItemRequest();
        req.setName("Iaurt Danone");
        req.setBrand(null);

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setGenericName("Iaurt");
        catalogProduct.setBrand("Danone");

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Iaurt Danone")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Iaurt Danone")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogProduct.getId())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Iaurt Danone")).thenReturn(List.of());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        // Matches because the user typed the brand in the name
        assertThat(itemCaptor.getValue().getCatalogItem()).isEqualTo(catalogProduct);
    }

    @Test
    void resolveCatalogMatch_BrandMatch_UserNotProvided_CatalogNoBrand_ExactSpecificNameMatch() {
        ItemRequest req = new ItemRequest();
        req.setName("Paine Feliata");
        req.setBrand(null);

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setSpecificName("Paine feliata"); // Case insensitive check
        catalogProduct.setBrand(null);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Paine Feliata")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Paine Feliata")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogProduct.getId())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Paine Feliata")).thenReturn(List.of());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCatalogItem()).isEqualTo(catalogProduct);
    }

    @Test
    void resolveCatalogMatch_BrandMatch_UserNotProvided_CatalogNoBrand_NameContainsSpecificName() {
        ItemRequest req = new ItemRequest();
        req.setName("Paine de casa");
        req.setBrand(null);

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setSpecificName("Paine");
        catalogProduct.setBrand(null);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        lenient().when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Paine de casa")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Paine de casa")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogProduct.getId())).thenReturn(List.of());
        when(itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, "Paine de casa")).thenReturn(List.of());
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.addItemToList(listId, req, userEmail);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getCatalogItem()).isEqualTo(catalogProduct);
    }

    @Test
    void updateItemFromSync_ThrowsExceptionWhenJsonIsCompletelyInvalid() {
        com.p2ps.dto.ListUpdatePayload payload = new com.p2ps.dto.ListUpdatePayload();
        payload.setAction(com.p2ps.dto.ActionType.UPDATE);
        // This is invalid JSON but is just a normal string
        payload.setContent("Not a JSON object");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemDTO result = itemService.updateItemFromSync(itemId, payload);

        // Should gracefully fallback and treat it as a raw string name update
        assertThat(result.getName()).isEqualTo("Not a JSON object");
    }

    @Test
    void updateItemFromSync_ShouldResolveCatalogAndExternalIdOnUpdateAction() {
        ListUpdatePayload payload = new ListUpdatePayload();
        payload.setAction(ActionType.UPDATE);
        payload.setContent("{\"name\":\"Coca Cola\",\"brand\":\"Coca Cola\"}");

        ProductCatalog catalogProduct = new ProductCatalog();
        catalogProduct.setId(UUID.randomUUID());
        catalogProduct.setSpecificName("Coca Cola Zero");
        catalogProduct.setBrand("Coca Cola");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Coca Cola Coca Cola")).thenReturn(null);
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Coca Cola")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Coca Cola Coca Cola")).thenReturn(List.of(catalogProduct));
        when(itemRepository.findRoutableExternalItemIdByName("Coca Cola")).thenReturn(Optional.of("route-123"));

        ItemDTO result = itemService.updateItemFromSync(itemId, payload);

        assertThat(result.getCatalogId()).isEqualTo(catalogProduct.getId());
        assertThat(result.getExternalItemId()).isEqualTo("route-123");
    }

    // ==========================================
    // deleteCompletedItems tests
    // ==========================================

    @Test
    void deleteCompletedItems_DeletesCheckedItemsAndReturnsIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Item checked1 = new Item();
        checked1.setId(id1);
        checked1.setChecked(true);
        checked1.setShoppingList(mockList);

        Item checked2 = new Item();
        checked2.setId(id2);
        checked2.setChecked(true);
        checked2.setShoppingList(mockList);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        when(itemRepository.findByShoppingListIdAndIsCheckedTrue(listId)).thenReturn(List.of(checked1, checked2));

        List<UUID> result = itemService.deleteCompletedItems(listId, userEmail);

        assertThat(result).containsExactlyInAnyOrder(id1, id2);
        verify(itemRepository).deleteAll(List.of(checked1, checked2));
    }

    @Test
    void deleteCompletedItems_ReturnsEmptyListWhenNoCheckedItems() {
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(mockList));
        when(itemRepository.findByShoppingListIdAndIsCheckedTrue(listId)).thenReturn(List.of());

        List<UUID> result = itemService.deleteCompletedItems(listId, userEmail);

        assertThat(result).isEmpty();
        verify(itemRepository, never()).deleteAll(any());
    }

    @Test
    void deleteCompletedItems_ThrowsWhenListNotFound() {
        when(shoppingListRepository.findById(listId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.deleteCompletedItems(listId, userEmail))
                .isInstanceOf(ShoppingListNotFoundException.class);
    }

    @Test
    void deleteCompletedItems_ThrowsWhenUserHasNoPermission() {
        Users otherUser = new Users();
        otherUser.setId(999);
        otherUser.setEmail("other@example.com");

        ShoppingList restrictedList = new ShoppingList();
        restrictedList.setId(listId);
        restrictedList.setUser(otherUser);

        when(shoppingListRepository.findById(listId)).thenReturn(Optional.of(restrictedList));

        assertThatThrownBy(() -> itemService.deleteCompletedItems(listId, userEmail))
                .isInstanceOf(ListAccessDeniedException.class);
    }

    // ==========================================
    // CLAIM ITEM TESTS
    // ==========================================

    @Test
    void claimItem_SetsClaimedByAndTimestamp() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemDTO result = itemService.claimItem(itemId, "alice@test.com");

        assertThat(result.getClaimedBy()).isEqualTo("alice@test.com");
        assertThat(result.getClaimedAt()).isNotNull();
        verify(itemRepository).save(mockItem);
    }

    @Test
    void claimItem_ClearsClaimWhenEmailIsNull() {
        mockItem.setClaimedBy("alice@test.com");
        mockItem.setClaimedAt(1000L);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemDTO result = itemService.claimItem(itemId, null);

        assertThat(result.getClaimedBy()).isNull();
        assertThat(result.getClaimedAt()).isNull();
        verify(itemRepository).save(mockItem);
    }

    @Test
    void claimItem_ThrowsWhenItemNotFound() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.claimItem(itemId, "alice@test.com"))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void claimItem_UpdatesLastUpdatedTimestamp() {
        mockItem.setLastUpdatedTimestamp(1000L);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        itemService.claimItem(itemId, "alice@test.com");

        assertThat(mockItem.getLastUpdatedTimestamp()).isGreaterThan(1000L);
    }

    @Test
    void processReceiptItem_shouldRecordStorePriceAndHistoryWhenCatalogExists() {
        ProductCatalog catalog = new ProductCatalog();
        catalog.setId(UUID.randomUUID());
        mockItem.setCatalogItem(catalog);
        mockItem.setName("Lapte");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Lapte")).thenReturn(null);

        ItemDTO result = itemService.processReceiptItem(itemId, new BigDecimal("7.50"), "Mega", userEmail);

        assertThat(result.isChecked()).isTrue();
        verify(storePriceService).recordStorePrice(catalog, "Mega", new BigDecimal("7.50"));
        verify(historyRepository).save(any(UserProductHistory.class));
    }

    @Test
    void processReceiptItem_shouldSaveOnlyHistoryWhenCatalogMissing() {
        mockItem.setCatalogItem(null);
        mockItem.setName("Lapte");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByUser_IdAndCustomNameIgnoreCase(mockUser.getId(), "Lapte")).thenReturn(null);
        when(catalogRepository.searchByKeywordStrict("Lapte")).thenReturn(List.of());

        ItemDTO result = itemService.processReceiptItem(itemId, new BigDecimal("7.50"), "Mega", userEmail);

        assertThat(result.isChecked()).isTrue();
        verify(storePriceService, never()).recordStorePrice(any(), anyString(), any());
        verify(historyRepository).save(any(UserProductHistory.class));
    }

    @Test
    void processReceiptItem_shouldThrowWhenPriceIsNegative() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> itemService.processReceiptItem(itemId, new BigDecimal("-1.00"), "Mega", userEmail))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("zero or positive");
    }

    @Test
    void processReceiptItem_shouldThrowWhenUserHasNoPermission() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> itemService.processReceiptItem(itemId, BigDecimal.ONE, "Mega", "other@example.com"))
                .isInstanceOf(ListAccessDeniedException.class);
    }
}
