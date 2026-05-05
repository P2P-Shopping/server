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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Service
public class ItemService {

    private static final String ITEM_NOT_FOUND = "Item not found";

    private final ItemRepository itemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final UserProductHistoryRepository historyRepository;

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

        Item item = new Item();
        item.setName(request.getName());
        item.setShoppingList(list);

        item.setBrand(request.getBrand());
        item.setQuantity(request.getQuantity());
        item.setPrice(request.getPrice());
        item.setCategory(request.getCategory());

        item.setRecurrent(request.getIsRecurrent() != null && request.getIsRecurrent());

        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        item.setCreatedAt(System.currentTimeMillis());

        saveToHistory(item.getName(), list.getUser());
        return mapToDTO(itemRepository.save(item));
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

        // Logica de Checkbox + Trigger Echipa 3
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
            // If content is present, it might be a JSON string of ItemDTO or just the name
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                ItemDTO dto = mapper.readValue(payload.getContent(), ItemDTO.class);
                if (dto.getName() != null) item.setName(dto.getName());
                if (dto.getBrand() != null) item.setBrand(dto.getBrand());
                if (dto.getQuantity() != null) item.setQuantity(dto.getQuantity());
                if (dto.getPrice() != null) item.setPrice(dto.getPrice());
                if (dto.getCategory() != null) item.setCategory(dto.getCategory());
            } catch (Exception _) {
                // Fallback: treat content as the item name
                item.setName(payload.getContent());
            }
        }

        item.setLastUpdatedTimestamp(System.currentTimeMillis());
        return mapToDTO(itemRepository.save(item));
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

        List<Item> items = new ArrayList<>();
        for (ItemRequest request : requests) {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new ListValidationException("Item name cannot be empty");

            }
            validatePrice(request.getPrice());

            Item item = new Item();
            item.setName(request.getName());
            item.setShoppingList(list);
            item.setBrand(request.getBrand());
            item.setQuantity(request.getQuantity());
            item.setPrice(request.getPrice());
            item.setCategory(request.getCategory());
            item.setRecurrent(request.getIsRecurrent() != null && request.getIsRecurrent());
            item.setLastUpdatedTimestamp(System.currentTimeMillis());
            item.setCreatedAt(System.currentTimeMillis());

            saveToHistory(item.getName(), list.getUser());
            items.add(item);
        }

        List<Item> saved = itemRepository.saveAll(items);

        return saved.stream().map(this::mapToDTO).toList();
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
