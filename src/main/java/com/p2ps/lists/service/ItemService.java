package com.p2ps.lists.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.ai.service.AiService;
import com.p2ps.auth.model.Users;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
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
import com.p2ps.util.QuantityParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);
    private static final String ITEM_NOT_FOUND = "Item not found";
    private static final String SHOPPING_LIST_NOT_FOUND = "Shopping list not found";
    private static final List<String> RECEIPT_JUNK_KEYWORDS = List.of(
            "garantie",
            "extragarantie",
            "sgr",
            "returo",
            "taxa ambalaj",
            "ambalaj",
            "punga",
            "sacosa",
            "sacoșa",
            "voucher",
            "discount",
            "reducere",
            "livrare",
            "transport"
    );

    private final ItemRepository itemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final UserProductHistoryRepository historyRepository;
    private final ProductCatalogRepository catalogRepository;
    private final AiService aiService;
    private final StorePriceService storePriceService;

    private final ItemService self;

    public ItemService(ItemRepository itemRepository,
                       ShoppingListRepository shoppingListRepository,
                       UserProductHistoryRepository historyRepository,
                       ProductCatalogRepository catalogRepository,
                       AiService aiService,
                       StorePriceService storePriceService,
                       @Lazy ItemService self) {
        this.itemRepository = itemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.historyRepository = historyRepository;
        this.catalogRepository = catalogRepository;
        this.aiService = aiService;
        this.storePriceService = storePriceService;
        this.self = self;
    }

    @Transactional
    public ReceiptProcessingResult recordReceiptItem(ParsedItemResponse item, String storeName, Users user) {
        if (item == null || isReceiptJunkItem(item)) {
            return ReceiptProcessingResult.createIgnored();
        }

        String specificName = firstNonBlank(item.getSpecificName(), item.getGenericName());
        if (specificName == null || specificName.isBlank()) {
            return ReceiptProcessingResult.createIgnored();
        }

        BigDecimal validPrice = sanitizeNonNegativePrice(item.getPrice());
        ProductCatalog catalogMatch = resolveCatalogMatch(item, user);

        if (catalogMatch != null && validPrice != null) {
            storePriceService.recordStorePrice(catalogMatch, storeName, validPrice);
        }

        saveToHistory(
                null,
                user,
                specificName,
                storeName,
                item.getBrand(),
                item.getCategory(),
                validPrice,
                catalogMatch
        );

        return new ReceiptProcessingResult(false, catalogMatch);
    }

    public record ReceiptProcessingResult(boolean ignored, ProductCatalog catalogMatch) {
        public static ReceiptProcessingResult createIgnored() {
            return new ReceiptProcessingResult(true, null);
        }
    }

    private ProductCatalog resolveCatalogMatch(ParsedItemResponse item, Users user) {
        if (item.getCatalogId() != null && !item.getCatalogId().isBlank()) {
            try {
                UUID catalogId = UUID.fromString(item.getCatalogId().trim());
                ProductCatalog catalogById = catalogRepository.findById(catalogId).orElse(null);
                if (catalogById != null) {
                    return catalogById;
                }
            } catch (IllegalArgumentException _) {
                logger.debug("Ignoring invalid catalog id from receipt item: {}", item.getCatalogId());
            }
        }

        String specificName = firstNonBlank(item.getSpecificName(), item.getGenericName());
        if (specificName == null || specificName.isBlank()) {
            return null;
        }

        return resolveCatalogMatch(specificName, item.getBrand(), user);
    }

    private boolean isReceiptJunkItem(ParsedItemResponse item) {
        String normalizedText = Stream.of(item.getGenericName(), item.getSpecificName(), item.getBrand())
                .filter(Objects::nonNull)
                .map(this::normalizeReceiptText)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");

        if (normalizedText.isBlank()) {
            return false;
        }

        return RECEIPT_JUNK_KEYWORDS.stream().anyMatch(normalizedText::contains);
    }

    private String normalizeReceiptText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('ă', 'a')
                .replace('â', 'a')
                .replace('î', 'i')
                .replace('ș', 's')
                .replace('ş', 's')
                .replace('ț', 't')
                .replace('ţ', 't');
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private BigDecimal sanitizeNonNegativePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }
        return price;
    }

    private String normalizeBrand(String brand) {
        if (brand == null || brand.isBlank()) return "";
        return brand.trim().toLowerCase();
    }

    private List<Item> findExactListMatches(UUID listId, String name, String brand) {
        String normalizedBrand = normalizeBrand(brand);
        List<Item> candidates = itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, name);
        List<Item> exactMatches = new ArrayList<>();

        for (Item item : candidates) {
            String itemBrand = normalizeBrand(item.getBrand());
            if (normalizedBrand.equals(itemBrand)) {
                exactMatches.add(item);
            }
        }
        return exactMatches;
    }

    @Transactional
    public ItemDTO addItemToList(UUID listId, ItemRequest request, String userEmail) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ListValidationException("Item name cannot be empty");
        }
        validatePrice(request.getPrice());
        QuantityParser.parse(request.getQuantity());

        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (!list.canBeModifiedBy(userEmail)) {
            throw new ListAccessDeniedException("You do not have permission to add items to this list");
        }

        String normalizedItemName = request.getName().trim();
        ProductCatalog catalogMatch = resolveCatalogMatch(normalizedItemName, request.getBrand(), list.getUser());

        List<Item> existingItems = new ArrayList<>();
        if (catalogMatch != null) {
            existingItems = itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogMatch.getId());
        }

        if (existingItems.isEmpty()) {
            existingItems = findExactListMatches(listId, normalizedItemName, request.getBrand());
        }

        if (!existingItems.isEmpty()) {
            return mergeAndSaveItem(listId, request, userEmail, existingItems, catalogMatch);
        }

        return createAndSaveNewItem(listId, request, userEmail, list, normalizedItemName, catalogMatch);
    }

    private ItemDTO mergeAndSaveItem(UUID listId, ItemRequest request, String userEmail, List<Item> existingItems, ProductCatalog catalogMatch) {
        Item primaryItem = existingItems.get(0);

        for (int i = 1; i < existingItems.size(); i++) {
            Item duplicate = existingItems.get(i);
            primaryItem.setQuantity(sumStringQuantities(primaryItem.getQuantity(), duplicate.getQuantity()));
            itemRepository.delete(duplicate);
        }

        primaryItem.setQuantity(sumStringQuantities(primaryItem.getQuantity(), request.getQuantity()));
        primaryItem.setLastUpdatedTimestamp(System.currentTimeMillis());

        updateItemFields(primaryItem, request);
        attachRoutableExternalItemId(primaryItem);

        if (catalogMatch != null) {
            primaryItem.setCatalogItem(catalogMatch);
        }

        try {
            return mapToDTO(itemRepository.save(primaryItem));
        } catch (org.springframework.dao.DataIntegrityViolationException _) {
            return self.addItemToList(listId, request, userEmail);
        }
    }

    private ItemDTO createAndSaveNewItem(UUID listId, ItemRequest request, String userEmail, ShoppingList list, String normalizedItemName, ProductCatalog catalogMatch) {
        Item item = new Item();

        item.setName(normalizedItemName);
        item.setCatalogItem(catalogMatch);
        item.setShoppingList(list);
        item.setBrand(request.getBrand());
        item.setQuantity(request.getQuantity());
        item.setPrice(request.getPrice());
        item.setCategory(request.getCategory());
        item.setRecurrent(request.getIsRecurrent() != null && request.getIsRecurrent());
        item.setPositionIndex(request.getPositionIndex() != null ? request.getPositionIndex() : (double) System.currentTimeMillis());
        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        item.setCreatedAt(System.currentTimeMillis());
        attachRoutableExternalItemId(item);

        try {
            Item savedItem = itemRepository.save(item);
            saveToHistory(savedItem, list.getUser(), normalizedItemName, list.getFinalStore());
            return mapToDTO(savedItem);
        } catch (org.springframework.dao.DataIntegrityViolationException _) {
            return self.addItemToList(listId, request, userEmail);
        }
    }

    private void updateItemFields(Item item, ItemRequest request) {
        if (request.getBrand() != null && (item.getBrand() == null || item.getBrand().isBlank())) {
            item.setBrand(request.getBrand());
        }
        if (request.getPrice() != null && (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) == 0)) {
            item.setPrice(request.getPrice());
        }
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        if (request.getIsRecurrent() != null) item.setRecurrent(request.getIsRecurrent());
        if (request.getPositionIndex() != null) item.setPositionIndex(request.getPositionIndex());
    }

    @Transactional
    public List<ItemDTO> addItemsToList(UUID listId, List<ItemRequest> requests, String userEmail) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }

        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (!list.canBeModifiedBy(userEmail)) {
            throw new ListAccessDeniedException("You do not have permission to add items to this list");
        }

        Map<String, Item> batchMap = new LinkedHashMap<>();
        for (ItemRequest request : requests) {
            processItemRequest(listId, list, request, batchMap);
        }

        List<Item> pendingItems = new ArrayList<>(batchMap.values());
        Map<UUID, Item> trackingMap = new HashMap<>();
        List<ItemDTO> dtosToValidate = prepareItemsForAiValidation(pendingItems, trackingMap);

        List<ItemDTO> validatedDtos = performAiPostValidation(dtosToValidate);

        List<Item> itemsToSave = applyAiValidationResults(validatedDtos, trackingMap, list.getUser());

        List<Item> saved = itemRepository.saveAll(itemsToSave);
        return saved.stream().map(this::mapToDTO).toList();
    }

    private List<ItemDTO> prepareItemsForAiValidation(List<Item> pendingItems, Map<UUID, Item> trackingMap) {
        List<ItemDTO> dtosToValidate = new ArrayList<>();
        for (Item item : pendingItems) {
            ItemDTO dto = mapToDTO(item);
            UUID trackingId = dto.getId();

            if (trackingId == null) {
                trackingId = UUID.randomUUID();
                dto.setId(trackingId);
            }

            trackingMap.put(trackingId, item);
            dtosToValidate.add(dto);
        }
        return dtosToValidate;
    }

    private List<ItemDTO> performAiPostValidation(List<ItemDTO> dtosToValidate) {
        try {
            return aiService.postValidateAndFilterReceiptItems(dtosToValidate);
        } catch (Exception e) {
            logger.warn("AI post-validation failed, falling back to original list", e);
            return dtosToValidate;
        }
    }
    private List<Item> applyAiValidationResults(List<ItemDTO> validatedDtos, Map<UUID, Item> trackingMap, com.p2ps.auth.model.Users user) {
        List<Item> itemsToSave = new ArrayList<>();

        for (ItemDTO dto : validatedDtos) {
            if (dto.getId() != null && !trackingMap.containsKey(dto.getId())) {
                logger.error("AI hallucinated an unknown ID: {}. Triggering full fallback.", dto.getId());
                return new ArrayList<>(trackingMap.values());
            }

            Item originalItem = trackingMap.get(dto.getId());
            if (originalItem != null) {
                applyRefinedAiUpdates(originalItem, dto);
                ensureResolvableProductLink(originalItem, user);
                itemsToSave.add(originalItem);
            }
        }
        return itemsToSave;
    }

    private void applyRefinedAiUpdates(Item originalItem, ItemDTO dto) {
        if (dto.getName() != null && !dto.getName().isBlank()) {
            originalItem.setName(dto.getName());
        }
        if (dto.getQuantity() != null) {
            QuantityParser.parse(dto.getQuantity());
            originalItem.setQuantity(dto.getQuantity());
        }
    }

    private void processItemRequest(UUID listId, ShoppingList list, ItemRequest request, Map<String, Item> batchMap) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ListValidationException("Item name cannot be empty");
        }
        validatePrice(request.getPrice());
        QuantityParser.parse(request.getQuantity());

        String normalizedItemName = request.getName().trim();
        ProductCatalog catalogMatch = resolveCatalogMatch(normalizedItemName, request.getBrand(), list.getUser());

        String mapKey;
        if (catalogMatch != null) {
            mapKey = "cat_" + catalogMatch.getId().toString();
        } else {
            String normBrand = normalizeBrand(request.getBrand());
            mapKey = "name_" + normalizedItemName.toLowerCase() + "_brand_" + normBrand;
        }

        if (batchMap.containsKey(mapKey)) {
            mergeIntoBatch(batchMap.get(mapKey), request, catalogMatch);
        } else {
            resolveAndMergeFromDb(listId, list, request, batchMap, normalizedItemName, mapKey, catalogMatch);
        }
    }

    private void mergeIntoBatch(Item existingInBatch, ItemRequest request, ProductCatalog catalogMatch) {
        existingInBatch.setQuantity(sumStringQuantities(existingInBatch.getQuantity(), request.getQuantity()));
        existingInBatch.setLastUpdatedTimestamp(System.currentTimeMillis());

        updateItemFields(existingInBatch, request);

        if (catalogMatch != null && existingInBatch.getCatalogItem() == null) {
            existingInBatch.setCatalogItem(catalogMatch);
        }
    }

    private void resolveAndMergeFromDb(UUID listId, ShoppingList list, ItemRequest request, Map<String, Item> batchMap, String normalizedItemName, String mapKey, ProductCatalog catalogMatch) {
        List<Item> existingInDb = new ArrayList<>();
        if (catalogMatch != null) {
            existingInDb = itemRepository.findByShoppingListIdAndCatalogItem_Id(listId, catalogMatch.getId());
        }

        if (existingInDb.isEmpty()) {
            existingInDb = findExactListMatches(listId, normalizedItemName, request.getBrand());
        }

        if (!existingInDb.isEmpty()) {
            Item primaryItem = existingInDb.get(0);
            for (int i = 1; i < existingInDb.size(); i++) {
                Item duplicate = existingInDb.get(i);
                primaryItem.setQuantity(sumStringQuantities(primaryItem.getQuantity(), duplicate.getQuantity()));
                itemRepository.delete(duplicate);
            }
            primaryItem.setQuantity(sumStringQuantities(primaryItem.getQuantity(), request.getQuantity()));
            primaryItem.setLastUpdatedTimestamp(System.currentTimeMillis());

            updateItemFields(primaryItem, request);
            attachRoutableExternalItemId(primaryItem);

            if (catalogMatch != null) {
                primaryItem.setCatalogItem(catalogMatch);
            }

            batchMap.put(mapKey, primaryItem);
        } else {
            batchMap.put(mapKey, createNewItemForBatch(list, request, normalizedItemName, catalogMatch));
        }
    }

    private Item createNewItemForBatch(ShoppingList list, ItemRequest request, String normalizedItemName, ProductCatalog catalogMatch) {
        Item newItem = new Item();

        newItem.setName(normalizedItemName);
        newItem.setCatalogItem(catalogMatch);
        newItem.setShoppingList(list);
        newItem.setBrand(request.getBrand());
        newItem.setQuantity(request.getQuantity());
        newItem.setPrice(request.getPrice());
        newItem.setCategory(request.getCategory());
        newItem.setRecurrent(request.getIsRecurrent() != null && request.getIsRecurrent());
        newItem.setPositionIndex(request.getPositionIndex() != null ? request.getPositionIndex() : (double) System.currentTimeMillis());
        newItem.setLastUpdatedTimestamp(System.currentTimeMillis());
        newItem.setCreatedAt(System.currentTimeMillis());
        attachRoutableExternalItemId(newItem);
        saveToHistory(newItem, list.getUser(), normalizedItemName, list.getFinalStore());
        return newItem;
    }

    @Transactional
    public List<ItemDTO> addItemsToListWithRetry(UUID listId, List<ItemRequest> requests, String userEmail) {
        try {
            return self.addItemsToList(listId, requests, userEmail);
        } catch (org.springframework.dao.DataIntegrityViolationException _) {
            return self.addItemsToList(listId, requests, userEmail);
        }
    }

    @Transactional
    public ItemDTO updateItem(UUID itemId, ItemRequest request, String userEmail) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        ShoppingList list = item.getShoppingList();
        boolean canModify = list.canBeModifiedBy(userEmail);
        boolean canCheck = list.canCheckItems(userEmail);

        if (!canModify && !canCheck) {
            throw new ListAccessDeniedException("You do not have permission to edit this item");
        }

        if (!canModify) {
            // User is a GUEST. Verify they only change isChecked
            validateGuestAccess(item, request);
        }

        applyUpdatableFields(item, request);

        if (request.getName() != null || request.getBrand() != null) {
            item.setCatalogItem(resolveCatalogMatch(item.getName(), item.getBrand(), item.getShoppingList().getUser()));
        }

        applyCheckedState(item, request);

        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        attachRoutableExternalItemId(item);

        return mapToDTO(itemRepository.save(item));
    }

    private void applyUpdatableFields(Item item, ItemRequest request) {
        if (request.getName() != null) {
            if (request.getName().trim().isEmpty()) {
                throw new ListValidationException("Item name cannot be empty");
            }
            item.setName(request.getName().trim());
        }

        if (request.getBrand() != null) item.setBrand(request.getBrand());
        if (request.getQuantity() != null) {
            QuantityParser.parse(request.getQuantity());
            item.setQuantity(request.getQuantity());
        }
        validatePrice(request.getPrice());
        if (request.getPrice() != null) item.setPrice(request.getPrice());
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        if (request.getIsRecurrent() != null) item.setRecurrent(request.getIsRecurrent());
        if (request.getPositionIndex() != null) item.setPositionIndex(request.getPositionIndex());
    }

    private void applyCheckedState(Item item, ItemRequest request) {
        if (request.getIsChecked() == null) return;
        boolean wasChecked = item.isChecked();
        item.setChecked(request.getIsChecked());
        if (item.isChecked() && !wasChecked) {
            saveToHistory(item, item.getShoppingList().getUser(), item.getName(), item.getShoppingList().getFinalStore());
        }
    }

    private void validateGuestAccess(Item item, ItemRequest request) {
        if (isFieldChanged(request.getName(), item.getName(), (req, cur) -> !req.trim().equals(cur))
                || isFieldChanged(request.getBrand(), item.getBrand())
                || isFieldChanged(request.getQuantity(), item.getQuantity())
                || isPriceChanged(request.getPrice(), item.getPrice())
                || isFieldChanged(request.getCategory(), item.getCategory())
                || isRecurrentChanged(request.getIsRecurrent(), item.isRecurrent())
                || isFieldChanged(request.getPositionIndex(), item.getPositionIndex())) {
            throw new ListAccessDeniedException("GUEST users can only check or uncheck items");
        }
    }

    private <T> boolean isFieldChanged(T newValue, T currentValue) {
        return newValue != null && !newValue.equals(currentValue);
    }

    private <T> boolean isFieldChanged(T newValue, T currentValue, java.util.function.BiFunction<T, T, Boolean> comparer) {
        return newValue != null && comparer.apply(newValue, currentValue);
    }

    private boolean isPriceChanged(java.math.BigDecimal newValue, java.math.BigDecimal currentValue) {
        return newValue != null && (currentValue == null || newValue.compareTo(currentValue) != 0);
    }

    private boolean isRecurrentChanged(Boolean newValue, boolean currentValue) {
        return newValue != null && newValue != currentValue;
    }


    @Transactional
    public ItemDTO updateItemStatus(UUID itemId, boolean checked, Long clientTimestamp) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        boolean wasChecked = item.isChecked();

        item.setChecked(checked);
        item.setLastUpdatedTimestamp(System.currentTimeMillis());

        if (checked && !wasChecked) {
            saveToHistory(item, item.getShoppingList().getUser(), item.getName(), item.getShoppingList().getFinalStore());
        }

        return mapToDTO(itemRepository.save(item));
    }

    @Transactional
    public ItemDTO updateItemFromSync(UUID itemId, com.p2ps.dto.ListUpdatePayload payload) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        if (payload.getChecked() != null) {
            boolean wasChecked = item.isChecked();
            item.setChecked(payload.getChecked());
            if (item.isChecked() && !wasChecked) {
                saveToHistory(item, item.getShoppingList().getUser(), item.getName(), item.getShoppingList().getFinalStore());
            }
        }

        if (payload.getAction() == com.p2ps.dto.ActionType.UPDATE && payload.getContent() != null) {
            applySyncContent(item, payload.getContent());
            item.setCatalogItem(resolveCatalogMatch(item.getName(), item.getBrand(), item.getShoppingList().getUser()));
            attachRoutableExternalItemId(item);
        }

        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        return mapToDTO(itemRepository.save(item));
    }

    @Transactional
    public ItemDTO claimItem(UUID itemId, String claimedByEmail) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        item.setClaimedBy(claimedByEmail);
        item.setClaimedAt(claimedByEmail != null ? System.currentTimeMillis() : null);
        item.setLastUpdatedTimestamp(System.currentTimeMillis());

        return mapToDTO(itemRepository.save(item));
    }

    private void applySyncContent(Item item, String content) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            ItemDTO dto = mapper.readValue(content, ItemDTO.class);
            if (dto.getName() != null) {
                if (dto.getName().trim().isEmpty()) throw new ListValidationException("Item name cannot be empty");
                item.setName(dto.getName());
            }
            if (dto.getBrand() != null) item.setBrand(dto.getBrand());
            if (dto.getQuantity() != null) {
                QuantityParser.parse(dto.getQuantity());
                item.setQuantity(dto.getQuantity());
            }if (dto.getPrice() != null) {
                validatePrice(dto.getPrice());
                item.setPrice(dto.getPrice());
            }
            if (dto.getCategory() != null) item.setCategory(dto.getCategory());
            if (dto.getPositionIndex() != null) item.setPositionIndex(dto.getPositionIndex());
        } catch (ListValidationException e) {
            throw e;
        } catch (Exception _) {
            if (content.trim().isEmpty()) throw new ListValidationException("Item name cannot be empty");
            item.setName(content);
        }
    }

    @Transactional
    public void deleteItem(UUID itemId, String userEmail) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        if (!item.getShoppingList().canBeModifiedBy(userEmail)) {
            throw new ListAccessDeniedException("You do not have permission to delete this item");
        }

        itemRepository.delete(item);
    }

    @Transactional
    public List<UUID> deleteCompletedItems(UUID listId, String userEmail) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (!list.canBeModifiedBy(userEmail)) {
            throw new ListAccessDeniedException("You do not have permission to delete items from this list");
        }

        List<Item> checkedItems = itemRepository.findByShoppingListIdAndIsCheckedTrue(listId);

        if (checkedItems.isEmpty()) {
            return List.of();
        }

        List<UUID> deletedIds = checkedItems.stream().map(Item::getId).toList();
        itemRepository.deleteAll(checkedItems);

        return deletedIds;
    }

    @Transactional
    public ItemDTO processReceiptItem(UUID itemId, BigDecimal receiptPrice, String storeName, String userEmail) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        if (!item.getShoppingList().canBeModifiedBy(userEmail)) {
            throw new ListAccessDeniedException("You do not have permission to modify this item");
        }

        if (receiptPrice != null) {
            validatePrice(receiptPrice);
            item.setPrice(receiptPrice);
        }
        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        item.setChecked(true);

        Item savedItem = itemRepository.save(item);

        ProductCatalog catalogItem = savedItem.getCatalogItem();

        if (catalogItem == null) {
            saveToHistory(savedItem, savedItem.getShoppingList().getUser(), savedItem.getName(), storeName);
        } else {
            if (storeName != null && !storeName.isBlank() && receiptPrice != null) {
                storePriceService.recordStorePrice(catalogItem, storeName, receiptPrice);
            }
            saveToHistory(savedItem, savedItem.getShoppingList().getUser(), savedItem.getName(), storeName);
        }

        return mapToDTO(savedItem);
    }

    private void validatePrice(BigDecimal price) {
        if (price != null && price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ListValidationException("Price must be zero or positive");
        }
    }

    private ItemDTO mapToDTO(Item item) {
        ItemDTO dto = new ItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setChecked(item.isChecked());
        dto.setBrand(item.getBrand());
        dto.setQuantity(item.getQuantity());
        dto.setPrice(item.getPrice());
        dto.setCategory(item.getCategory());
        dto.setRecurrent(item.isRecurrent());
        dto.setPositionIndex(item.getPositionIndex());
        dto.setLastUpdatedTimestamp(item.getLastUpdatedTimestamp());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setExternalItemId(item.getExternalItemId());
        if (item.getCatalogItem() != null) {
            dto.setCatalogId(item.getCatalogItem().getId());
        }
        dto.setClaimedBy(item.getClaimedBy());
        dto.setClaimedAt(item.getClaimedAt());
        return dto;
    }

    private void attachRoutableExternalItemId(Item item) {
        if (item.getExternalItemId() != null && !item.getExternalItemId().isBlank()) {
            return;
        }
        if (item.getName() == null || item.getName().isBlank()) {
            return;
        }

        itemRepository.findRoutableExternalItemIdByName(item.getName().trim())
                .ifPresent(item::setExternalItemId);
    }

    private void ensureResolvableProductLink(Item item, com.p2ps.auth.model.Users user) {
        if (item.getCatalogItem() == null) {
            ProductCatalog catalogMatch = resolveCatalogMatch(item.getName(), item.getBrand(), user);
            if (catalogMatch != null) {
                item.setCatalogItem(catalogMatch);
            }
        }
        attachRoutableExternalItemId(item);
    }

    private String sumStringQuantities(String oldQ, String newQ) {
        if (oldQ == null || oldQ.trim().isEmpty()) return newQ;
        if (newQ == null || newQ.trim().isEmpty()) return oldQ;

        return com.p2ps.util.QuantityParser.addQuantities(oldQ, newQ);
    }

    void saveToHistory(Item item, com.p2ps.auth.model.Users user, String rawName, String storeName) {
        saveToHistory(item, user, new HistoryContext(rawName, storeName, null, null, null, null));
    }

    void saveToHistory(
            Item item,
            com.p2ps.auth.model.Users user,
            String rawName,
            String storeName,
            String brand,
            String category,
            BigDecimal price,
            ProductCatalog catalogItem) {
        saveToHistory(item, user, new HistoryContext(rawName, storeName, brand, category, price, catalogItem));
    }

    private void saveToHistory(Item item, com.p2ps.auth.model.Users user, HistoryContext context) {
        String historyName = resolveHistoryName(item, context.rawName());
        if (historyName == null) {
            return;
        }

        UserProductHistory history = historyRepository.findByUser_IdAndCustomNameIgnoreCase(user.getId(), historyName);

        if (history == null) {
            history = new UserProductHistory();
            history.setUser(user);
            history.setCustomName(historyName);
        }

        if (item != null) {
            applyItemDetails(history, item);
        } else {
            applyExplicitDetails(history, context);
        }

        applyStoreName(history, context.storeName());

        ProductCatalog finalCatalogItem = resolveHistoryCatalogItem(item, context.catalogItem(), historyName, history.getBrand(), user);

        if (finalCatalogItem != null) {
            history.setCatalogItem(finalCatalogItem);
            if (item != null) {
                item.setCatalogItem(finalCatalogItem);
            }
        } else {
            history.setCatalogItem(null);
        }

        history.setLastAddedTimestamp(System.currentTimeMillis());
        historyRepository.save(history);
    }

    private String resolveHistoryName(Item item, String rawName) {
        if (rawName != null && !rawName.isBlank()) {
            return rawName;
        }
        if (item != null) {
            return item.getName();
        }
        return null;
    }

    private void applyItemDetails(UserProductHistory history, Item item) {
        applyTextIfPresent(history::setBrand, item.getBrand());
        applyTextIfPresent(history::setCategory, item.getCategory());
        applyPositivePrice(history, item.getPrice());
    }

    private void applyExplicitDetails(UserProductHistory history, HistoryContext context) {
        applyTextIfPresent(history::setBrand, context.brand());
        applyTextIfPresent(history::setCategory, context.category());
        applyPositivePrice(history, context.price());
    }

    private void applyStoreName(UserProductHistory history, String storeName) {
        applyTextIfPresent(history::setStoreName, storeName);
    }

    private ProductCatalog resolveHistoryCatalogItem(
            Item item,
            ProductCatalog catalogItem,
            String historyName,
            String brand,
            com.p2ps.auth.model.Users user) {
        ProductCatalog existingCatalogItem = item != null ? item.getCatalogItem() : null;
        if (existingCatalogItem != null) {
            return existingCatalogItem;
        }
        if (catalogItem != null) {
            return catalogItem;
        }
        return resolveCatalogMatch(historyName, brand, user);
    }

    private void applyTextIfPresent(java.util.function.Consumer<String> setter, String value) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private void applyPositivePrice(UserProductHistory history, BigDecimal price) {
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            history.setPrice(price);
        }
    }

    private record HistoryContext(
            String rawName,
            String storeName,
            String brand,
            String category,
            BigDecimal price,
            ProductCatalog catalogItem) {
    }

    private ProductCatalog resolveCatalogMatch(String itemName, String itemBrand, com.p2ps.auth.model.Users user) {
        String searchKeyword = itemName;
        if (itemBrand != null && !itemBrand.isBlank()) {
            searchKeyword = itemName + " " + itemBrand;
        }

        ProductCatalog historyMatch = findMatchInHistory(itemName, itemBrand, searchKeyword, user);
        if (historyMatch != null) {
            return historyMatch;
        }

        return findMatchInGlobalCatalog(itemName, itemBrand, searchKeyword);
    }

    private ProductCatalog findMatchInHistory(String itemName, String itemBrand, String searchKeyword, com.p2ps.auth.model.Users user) {
        if (user == null || user.getId() == null) {
            return null;
        }

        UserProductHistory exactHistory = historyRepository.findByUser_IdAndCustomNameIgnoreCase(user.getId(), searchKeyword);
        if (exactHistory == null && itemBrand != null && !itemBrand.isBlank()) {
            exactHistory = historyRepository.findByUser_IdAndCustomNameIgnoreCase(user.getId(), itemName);
        }

        if (exactHistory != null && exactHistory.getCatalogItem() != null) {
            ProductCatalog catalog = exactHistory.getCatalogItem();
            if (isBrandMatch(catalog, itemName, itemBrand)) {
                return catalog;
            }
        }

        return null;
    }

    private ProductCatalog findMatchInGlobalCatalog(String itemName, String itemBrand, String searchKeyword) {
        List<ProductCatalog> strictMatches = catalogRepository.searchByKeywordStrict(searchKeyword);

        if (strictMatches == null || strictMatches.isEmpty()) {
            return null;
        }

        for (ProductCatalog match : strictMatches) {
            if (isBrandMatch(match, itemName, itemBrand)) {
                return match;
            }
        }

        return null;
    }

    private boolean isBrandMatch(ProductCatalog catalog, String itemName, String requestedBrand) {
        boolean catalogHasBrand = catalog.getBrand() != null && !catalog.getBrand().isBlank();
        String specificNameLower = catalog.getSpecificName() != null ? catalog.getSpecificName().toLowerCase() : "";
        String itemNameLower = itemName.toLowerCase();

        if (requestedBrand != null && !requestedBrand.isBlank()) {
            String reqBrandLower = requestedBrand.trim().toLowerCase();

            if (catalogHasBrand) {
                return catalog.getBrand().equalsIgnoreCase(reqBrandLower);
            }
            return specificNameLower.contains(reqBrandLower);
        }

        if (catalogHasBrand) {
            String brandLower = catalog.getBrand().toLowerCase();
            if (itemNameLower.contains(brandLower)) {
                return true;
            }
        }

        String genericNameLower = catalog.getGenericName() != null ? catalog.getGenericName().toLowerCase() : "";
        return namesOverlap(itemNameLower, genericNameLower)
                || namesOverlap(itemNameLower, specificNameLower);
    }

    private boolean namesOverlap(String itemNameLower, String catalogNameLower) {
        return catalogNameLower != null
                && !catalogNameLower.isBlank()
                && (catalogNameLower.equals(itemNameLower)
                || itemNameLower.contains(catalogNameLower)
                || catalogNameLower.contains(itemNameLower));
    }
}
