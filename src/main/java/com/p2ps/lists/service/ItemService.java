package com.p2ps.lists.service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ItemService {

    private static final String ITEM_NOT_FOUND = "Item not found";

    private final ItemRepository itemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final UserProductHistoryRepository historyRepository;
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^([\\d.,]+)\\s*(.{0,50})$");

    @Autowired
    @Lazy
    private ItemService self;

    public ItemService(ItemRepository itemRepository, ShoppingListRepository shoppingListRepository, UserProductHistoryRepository historyRepository) {
        this.itemRepository = itemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public ItemDTO addItemToList(UUID listId, ItemRequest request, String userEmail) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ListValidationException("Item name cannot be empty");
        }
        validatePrice(request.getPrice());

        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException("Shopping list not found"));

        if (!list.canBeModifiedBy(userEmail)) {
            throw new ListAccessDeniedException("You do not have permission to add items to this list");
        }

        String normalizedItemName = request.getName().trim();
        List<Item> existingItems = itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, normalizedItemName);

        if (!existingItems.isEmpty()) {
            return mergeAndSaveItem(listId, request, userEmail, existingItems);
        }

        return createAndSaveNewItem(listId, request, userEmail, list, normalizedItemName);
    }

    private ItemDTO mergeAndSaveItem(UUID listId, ItemRequest request, String userEmail, List<Item> existingItems) {
        Item primaryItem = existingItems.get(0);

        for (int i = 1; i < existingItems.size(); i++) {
            Item duplicate = existingItems.get(i);
            primaryItem.setQuantity(sumStringQuantities(primaryItem.getQuantity(), duplicate.getQuantity()));
            itemRepository.delete(duplicate);
        }

        primaryItem.setQuantity(sumStringQuantities(primaryItem.getQuantity(), request.getQuantity()));
        primaryItem.setLastUpdatedTimestamp(System.currentTimeMillis());

        updateItemFields(primaryItem, request);

        try {
            return mapToDTO(itemRepository.save(primaryItem));
        } catch (org.springframework.dao.DataIntegrityViolationException _) {
            return self.addItemToList(listId, request, userEmail);
        }
    }

    private ItemDTO createAndSaveNewItem(UUID listId, ItemRequest request, String userEmail, ShoppingList list, String normalizedItemName) {
        Item item = new Item();
        item.setName(normalizedItemName);
        item.setShoppingList(list);
        item.setBrand(request.getBrand());
        item.setQuantity(request.getQuantity());
        item.setPrice(request.getPrice());
        item.setCategory(request.getCategory());
        item.setRecurrent(request.getIsRecurrent() != null && request.getIsRecurrent());
        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        item.setCreatedAt(System.currentTimeMillis());

        saveToHistory(item.getName(), list.getUser());
        try {
            return mapToDTO(itemRepository.save(item));
        } catch (org.springframework.dao.DataIntegrityViolationException _) {
            return self.addItemToList(listId, request, userEmail);
        }
    }

    private void updateItemFields(Item item, ItemRequest request) {
        if (request.getBrand() != null) item.setBrand(request.getBrand());
        if (request.getPrice() != null) item.setPrice(request.getPrice());
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        if (request.getIsRecurrent() != null) item.setRecurrent(request.getIsRecurrent());
    }

    @Transactional
    public List<ItemDTO> addItemsToList(UUID listId, List<ItemRequest> requests, String userEmail) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }

        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException("Shopping list not found"));

        if (!list.canBeModifiedBy(userEmail)) {
            throw new ListAccessDeniedException("You do not have permission to add items to this list");
        }

        Map<String, Item> batchMap = new LinkedHashMap<>();

        for (ItemRequest request : requests) {
            processItemRequest(listId, list, request, batchMap);
        }

        List<Item> saved = itemRepository.saveAll(new ArrayList<>(batchMap.values()));
        return saved.stream().map(this::mapToDTO).toList();
    }

    private void processItemRequest(UUID listId, ShoppingList list, ItemRequest request, Map<String, Item> batchMap) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ListValidationException("Item name cannot be empty");
        }
        validatePrice(request.getPrice());

        String normalizedItemName = request.getName().trim();
        String mapKey = normalizedItemName.toLowerCase();

        if (batchMap.containsKey(mapKey)) {
            mergeIntoBatch(batchMap.get(mapKey), request);
        } else {
            resolveAndMergeFromDb(listId, list, request, batchMap, normalizedItemName, mapKey);
        }
    }

    private void mergeIntoBatch(Item existingInBatch, ItemRequest request) {
        existingInBatch.setQuantity(sumStringQuantities(existingInBatch.getQuantity(), request.getQuantity()));
        existingInBatch.setLastUpdatedTimestamp(System.currentTimeMillis());
    }

    private void resolveAndMergeFromDb(UUID listId, ShoppingList list, ItemRequest request, Map<String, Item> batchMap, String normalizedItemName, String mapKey) {
        List<Item> existingInDb = itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, normalizedItemName);

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
            batchMap.put(mapKey, primaryItem);
        } else {
            batchMap.put(mapKey, createNewItemForBatch(list, request, normalizedItemName));
        }
    }

    private Item createNewItemForBatch(ShoppingList list, ItemRequest request, String normalizedItemName) {
        Item newItem = new Item();
        newItem.setName(normalizedItemName);
        newItem.setShoppingList(list);
        newItem.setBrand(request.getBrand());
        newItem.setQuantity(request.getQuantity());
        newItem.setPrice(request.getPrice());
        newItem.setCategory(request.getCategory());
        newItem.setRecurrent(request.getIsRecurrent() != null && request.getIsRecurrent());
        newItem.setLastUpdatedTimestamp(System.currentTimeMillis());
        newItem.setCreatedAt(System.currentTimeMillis());
        saveToHistory(newItem.getName(), list.getUser());
        return newItem;
    }

    @Transactional
    public List<ItemDTO> addItemsToListWithRetry(UUID listId, List<ItemRequest> requests, String userEmail) {
        try {
            return self.addItemsToList(listId, requests, userEmail);
        } catch (org.springframework.dao.DataIntegrityViolationException _) {
            // If any item in the batch fails due to a concurrent addition, retry the whole batch
            // The batch logic naturally handles existing DB items by merging.
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

        if (request.getIsChecked() != null && request.getIsChecked() != item.isChecked()) {
            item.setChecked(request.getIsChecked());
        }

        item.setLastUpdatedTimestamp(System.currentTimeMillis());

        return mapToDTO(itemRepository.save(item));
    }

    @Transactional
    public ItemDTO updateItemStatus(UUID itemId, boolean checked, Long clientTimestamp) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        item.setChecked(checked);
        item.setLastUpdatedTimestamp(System.currentTimeMillis());

        return mapToDTO(itemRepository.save(item));
    }

    @Transactional
    public ItemDTO updateItemFromSync(UUID itemId, com.p2ps.dto.ListUpdatePayload payload) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(ITEM_NOT_FOUND));

        if (payload.getChecked() != null) {
            item.setChecked(payload.getChecked());
        }

        if (payload.getAction() == com.p2ps.dto.ActionType.UPDATE && payload.getContent() != null) {
            applySyncContent(item, payload.getContent());
        }

        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        return mapToDTO(itemRepository.save(item));
    }

    private void applySyncContent(Item item, String content) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
        dto.setLastUpdatedTimestamp(item.getLastUpdatedTimestamp());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
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

    private void saveToHistory(String itemName, com.p2ps.auth.model.Users user) {
        UserProductHistory history = historyRepository.findByUser_IdAndCustomNameIgnoreCase(user.getId(), itemName);
        if (history == null) {
            history = new UserProductHistory();
            history.setUser(user);
            history.setCustomName(itemName);
        }
        history.setLastAddedTimestamp(System.currentTimeMillis());
        historyRepository.save(history);
    }
}
