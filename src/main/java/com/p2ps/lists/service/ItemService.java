package com.p2ps.lists.service;

import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListValidationException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ItemRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Service
public class ItemService {

    private static final String ITEM_NOT_FOUND = "Item not found";

    private final ItemRepository itemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^([\\d.,]+)\\s*(.*)$");

    public ItemService(ItemRepository itemRepository, ShoppingListRepository shoppingListRepository) {
        this.itemRepository = itemRepository;
        this.shoppingListRepository = shoppingListRepository;
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
        Optional<Item> existingItemOpt = itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, normalizedItemName);

        // If product exists, we update it
        if (existingItemOpt.isPresent()) {
            Item existingItem = existingItemOpt.get();

            String updatedQuantity = sumStringQuantities(existingItem.getQuantity(), request.getQuantity());
            existingItem.setQuantity(updatedQuantity);

            if (request.getBrand() != null) existingItem.setBrand(request.getBrand());
            if (request.getPrice() != null) existingItem.setPrice(request.getPrice());

            existingItem.setLastUpdatedTimestamp(System.currentTimeMillis());

            return mapToDTO(itemRepository.save(existingItem));
        }

        // If it does not exists, we create it
        Item item = new Item();
        item.setName(normalizedItemName);
        item.setShoppingList(list);

        item.setBrand(request.getBrand());
        item.setQuantity(request.getQuantity());
        item.setPrice(request.getPrice());
        item.setCategory(request.getCategory());

        item.setRecurrent(request.getIsRecurrent() != null && request.getIsRecurrent());
        item.setLastUpdatedTimestamp(System.currentTimeMillis());

        return mapToDTO(itemRepository.save(item));
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

        List<Item> itemsToSave = new ArrayList<>();

        for (ItemRequest request : requests) {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new ListValidationException("Item name cannot be empty");
            }
            validatePrice(request.getPrice());

            String normalizedItemName = request.getName().trim();
            Optional<Item> existingItemOpt = itemRepository.findByShoppingListIdAndNameIgnoreCase(listId, normalizedItemName);

            if (existingItemOpt.isPresent()) {
                // If exists, we update the quantity
                Item existingItem = existingItemOpt.get();
                String updatedQuantity = sumStringQuantities(existingItem.getQuantity(), request.getQuantity());
                existingItem.setQuantity(updatedQuantity);
                existingItem.setLastUpdatedTimestamp(System.currentTimeMillis());
                itemsToSave.add(existingItem);
            } else {
                // If it does not exist, we create a new item
                Item item = new Item();
                item.setName(normalizedItemName);
                item.setShoppingList(list);
                item.setBrand(request.getBrand());
                item.setQuantity(request.getQuantity());
                item.setPrice(request.getPrice());
                item.setCategory(request.getCategory());
                item.setRecurrent(request.getIsRecurrent() != null && request.getIsRecurrent());
                item.setLastUpdatedTimestamp(System.currentTimeMillis());

                itemsToSave.add(item);
            }
        }

        List<Item> saved = itemRepository.saveAll(itemsToSave);
        return saved.stream().map(this::mapToDTO).toList();
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
        return dto;
    }

    private String sumStringQuantities(String oldQ, String newQ) {
        if (oldQ == null || oldQ.trim().isEmpty()) return newQ;
        if (newQ == null || newQ.trim().isEmpty()) return oldQ;

        String cleanOld = oldQ.trim();
        String cleanNew = newQ.trim();

        Matcher matcherOld = QUANTITY_PATTERN.matcher(cleanOld);
        Matcher matcherNew = QUANTITY_PATTERN.matcher(cleanNew);

        // If both quantities are of type 'Same Unit' + 'Optional Text'
        if (matcherOld.matches() && matcherNew.matches()) {
            try {
                double valOld = Double.parseDouble(matcherOld.group(1).replace(",", "."));
                double valNew = Double.parseDouble(matcherNew.group(1).replace(",", "."));

                String unitOld = matcherOld.group(2).trim();
                String unitNew = matcherNew.group(2).trim();

                if (unitOld.equalsIgnoreCase(unitNew)) {
                    double sum = valOld + valNew;

                    String sumStr = (sum == (long) sum) ? String.valueOf((long) sum) : String.valueOf(sum);

                    if (!unitOld.isEmpty()) {
                        return sumStr + " " + unitOld;
                    }
                    return sumStr;
                }
            } catch (NumberFormatException e) {
                // fallback
            }
        }

        // Concatenate if quantity is not well formated
        return cleanOld + " + " + cleanNew;
    }

}
