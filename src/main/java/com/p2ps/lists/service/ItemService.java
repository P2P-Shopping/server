package com.p2ps.lists.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.service.AiService;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);
    private static final String ITEM_NOT_FOUND = "Item not found";
    private static final String SHOPPING_LIST_NOT_FOUND = "Shopping list not found";

    private final ItemRepository itemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final UserProductHistoryRepository historyRepository;
    private final ProductCatalogRepository catalogRepository;
    private final AiService aiService;
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^([\\d.,]+)\\s*(.{0,50})$");

    private final ItemService self;

    public ItemService(ItemRepository itemRepository,
                       ShoppingListRepository shoppingListRepository,
                       UserProductHistoryRepository historyRepository,
                       ProductCatalogRepository catalogRepository,
                       AiService aiService,
                       @Lazy ItemService self) {
        this.itemRepository = itemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.historyRepository = historyRepository;
        this.catalogRepository = catalogRepository;
        this.aiService = aiService;
        this.self = self;
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
            saveToHistory(savedItem, list.getUser(), normalizedItemName);
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

        List<Item> itemsToSave = applyAiValidationResults(validatedDtos, trackingMap);

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
    private List<Item> applyAiValidationResults(List<ItemDTO> validatedDtos, Map<UUID, Item> trackingMap) {
        List<Item> itemsToSave = new ArrayList<>();

        for (ItemDTO dto : validatedDtos) {
            // 💡 FIX 1: Protecție împotriva halucinațiilor de ID
            if (dto.getId() != null && !trackingMap.containsKey(dto.getId())) {
                logger.error("AI hallucinated an unknown ID: {}. Triggering full fallback.", dto.getId());
                return new ArrayList<>(trackingMap.values()); // Fallback total la lista originală!
            }

            Item originalItem = trackingMap.get(dto.getId());
            if (originalItem != null) {
                applyRefinedAiUpdates(originalItem, dto);
                itemsToSave.add(originalItem);
            }
        }
        return itemsToSave;
    }

    private void applyRefinedAiUpdates(Item originalItem, ItemDTO dto) {
        if (dto.getName() != null && !dto.getName().isBlank()) {
            originalItem.setName(dto.getName());
        }

        // 💡 FIX 2: Am șters blocul care actualiza brandul!
        // Respectăm warning-ul CodeRabbit. Lăsăm brandul exact așa cum l-a mapped backend-ul.

        if (dto.getQuantity() != null) {
            originalItem.setQuantity(dto.getQuantity());
        }
    }

    private void processItemRequest(UUID listId, ShoppingList list, ItemRequest request, Map<String, Item> batchMap) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ListValidationException("Item name cannot be empty");
        }
        validatePrice(request.getPrice());

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
        saveToHistory(newItem, list.getUser(), normalizedItemName);
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

        if (!item.getShoppingList().canBeModifiedBy(userEmail)) {
            throw new ListAccessDeniedException("You do not have permission to edit this item");
        }

        if (request.getName() != null) {
            if (request.getName().trim().isEmpty()) {
                throw new ListValidationException("Item name cannot be empty");
            }
            item.setName(request.getName());
        }

        if (request.getBrand() != null) item.setBrand(request.getBrand());
        if (request.getQuantity() != null) item.setQuantity(request.getQuantity());
        validatePrice(request.getPrice());
        if (request.getPrice() != null) item.setPrice(request.getPrice());
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        if (request.getIsRecurrent() != null) item.setRecurrent(request.getIsRecurrent());
        if (request.getPositionIndex() != null) item.setPositionIndex(request.getPositionIndex());

        if (request.getIsChecked() != null) {
            boolean wasChecked = item.isChecked();
            item.setChecked(request.getIsChecked());
            if (item.isChecked() && !wasChecked) {
                saveToHistory(item, item.getShoppingList().getUser(), item.getName());
            }
        }

        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        attachRoutableExternalItemId(item);

        return mapToDTO(itemRepository.save(item));
    }

    @Transactional
    public ItemDTO updateItemStatus(UUID itemId, boolean checked, Long clientTimestamp) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        boolean wasChecked = item.isChecked();

        item.setChecked(checked);
        item.setLastUpdatedTimestamp(System.currentTimeMillis());

        if (checked && !wasChecked) {
            saveToHistory(item, item.getShoppingList().getUser(), item.getName());
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
                saveToHistory(item, item.getShoppingList().getUser(), item.getName());
            }
        }

        if (payload.getAction() == com.p2ps.dto.ActionType.UPDATE && payload.getContent() != null) {
            applySyncContent(item, payload.getContent());
        }

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
            if (dto.getQuantity() != null) item.setQuantity(dto.getQuantity());
            if (dto.getPrice() != null) {
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

    private String sumStringQuantities(String oldQ, String newQ) {
        if (oldQ == null || oldQ.trim().isEmpty()) return newQ;
        if (newQ == null || newQ.trim().isEmpty()) return oldQ;

        Map<String, BigDecimal> unitSums = new LinkedHashMap<>();
        List<String> unparseableParts = new ArrayList<>();

        parseAndAccumulate(oldQ, unitSums, unparseableParts);
        parseAndAccumulate(newQ, unitSums, unparseableParts);

        List<String> finalParts = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : unitSums.entrySet()) {
            String valStr = entry.getValue().stripTrailingZeros().toPlainString();
            String unit = entry.getKey();

            if (unit.isEmpty()) {
                finalParts.add(valStr);
            } else {
                finalParts.add(valStr + " " + unit);
            }
        }

        finalParts.addAll(unparseableParts);

        return String.join(" + ", finalParts);
    }

    private void parseAndAccumulate(String quantityStr, Map<String, BigDecimal> unitSums, List<String> unparseableParts) {
        String[] parts = quantityStr.split("\\+");

        for (String part : parts) {
            String cleanPart = part.trim();
            if (cleanPart.isEmpty()) continue;

            Matcher matcher = QUANTITY_PATTERN.matcher(cleanPart);
            if (matcher.matches()) {
                try {
                    BigDecimal val = new BigDecimal(matcher.group(1).replace(",", "."));
                    String unit = matcher.group(2).trim().toLowerCase();
                    BigDecimal currentSum = unitSums.getOrDefault(unit, BigDecimal.ZERO);
                    unitSums.put(unit, currentSum.add(val));
                } catch (NumberFormatException _) {
                    unparseableParts.add(cleanPart);
                }
            } else {
                unparseableParts.add(cleanPart);
            }
        }
    }

    void saveToHistory(Item item, com.p2ps.auth.model.Users user, String rawName) {
        String historyName = (rawName != null && !rawName.isBlank()) ? rawName : item.getName();
        UserProductHistory history = historyRepository.findByUser_IdAndCustomNameIgnoreCase(user.getId(), historyName);
        if (history == null) {
            history = new UserProductHistory();
            history.setUser(user);
            history.setCustomName(historyName);
        }

        ProductCatalog catalogItem = item.getCatalogItem();

        if (catalogItem == null) {
            catalogItem = resolveCatalogMatch(historyName, item.getBrand(), user);
        }

        if (catalogItem != null) {
            history.setCatalogItem(catalogItem);
            item.setCatalogItem(catalogItem);
        } else {
            history.setCatalogItem(null);
        }

        history.setLastAddedTimestamp(System.currentTimeMillis());
        historyRepository.save(history);
    }

    private ProductCatalog resolveCatalogMatch(String itemName, String itemBrand, com.p2ps.auth.model.Users user) {
        String searchKeyword = itemName;
        if (itemBrand != null && !itemBrand.isBlank()) {
            searchKeyword = itemName + " " + itemBrand;
        }

        // 1. Try to find a match in the user's history
        ProductCatalog historyMatch = findMatchInHistory(itemName, itemBrand, searchKeyword, user);
        if (historyMatch != null) {
            return historyMatch;
        }

        // 2. Fallback to strict global catalog search
        return findMatchInGlobalCatalog(itemName, itemBrand, searchKeyword);
    }

    // --- Extracted helper methods to reduce Cognitive Complexity ---

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
                logger.debug("History match for '{}' (brand '{}'): {} (id={})", itemName, itemBrand, catalog.getSpecificName(), catalog.getId());
                return catalog;
            }
        }

        return null;
    }

    private ProductCatalog findMatchInGlobalCatalog(String itemName, String itemBrand, String searchKeyword) {
        List<ProductCatalog> strictMatches = catalogRepository.searchByKeywordStrict(searchKeyword);

        if (strictMatches == null || strictMatches.isEmpty()) {
            logger.debug("No strict catalog match found for '{}' (brand '{}'), leaving catalogId as null", itemName, itemBrand);
            return null;
        }

        for (ProductCatalog match : strictMatches) {
            if (isBrandMatch(match, itemName, itemBrand)) {
                logger.debug("Strict catalog match for '{}' (brand '{}'): {} (id={})", itemName, itemBrand, match.getSpecificName(), match.getId());
                return match;
            }
        }

        logger.debug("No strict catalog match found for '{}' (brand '{}') after filtering, leaving catalogId as null", itemName, itemBrand);
        return null;
    }

    private boolean isBrandMatch(ProductCatalog catalog, String itemName, String requestedBrand) {
        boolean catalogHasBrand = catalog.getBrand() != null && !catalog.getBrand().isBlank();
        String specificNameLower = catalog.getSpecificName() != null ? catalog.getSpecificName().toLowerCase() : "";
        String itemNameLower = itemName.toLowerCase();

        // 1. Daca utilizatorul a specificat explicit un brand
        if (requestedBrand != null && !requestedBrand.isBlank()) {
            String reqBrandLower = requestedBrand.trim().toLowerCase();

            if (catalogHasBrand) {
                return catalog.getBrand().equalsIgnoreCase(reqBrandLower);
            }
            return specificNameLower.contains(reqBrandLower);
        }

        // 2. Daca utilizatorul NU a specificat un brand, dar catalogul are unul
        if (catalogHasBrand) {
            return itemNameLower.contains(catalog.getBrand().toLowerCase());
        }

        // 3. Daca niciunul nu are brand oficial
        return specificNameLower.equals(itemNameLower) || itemNameLower.contains(specificNameLower);
    }
}