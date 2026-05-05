package com.p2ps.lists.service;

import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.repository.ProductCatalogRepository;
import com.p2ps.catalog.service.CatalogService; // Adaugat!
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Service
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);
    private static final String ITEM_NOT_FOUND = "Item not found";

    private final ItemRepository itemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final UserProductHistoryRepository historyRepository;
    private final ProductCatalogRepository catalogRepository;
    private final CatalogService catalogService; // Adaugat serviciul!

    // Constructor actualizat
    public ItemService(ItemRepository itemRepository, ShoppingListRepository shoppingListRepository,
                       UserProductHistoryRepository historyRepository, ProductCatalogRepository catalogRepository,
                       CatalogService catalogService) {
        this.itemRepository = itemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.historyRepository = historyRepository;
        this.catalogRepository = catalogRepository;
        this.catalogService = catalogService;
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

        // SALVĂM ÎN ISTORIC/CATALOG DOAR DACĂ A FOST BIFAT (CUMPĂRAT)
        if (checked) {
            saveToHistory(item, item.getShoppingList().getUser());
        }

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

            items.add(item);
        }

        List<Item> saved = itemRepository.saveAll(items);

        return saved.stream().map(this::mapToDTO).toList();
    }

    // Metoda actualizata primeste tot Item-ul!
    private void saveToHistory(Item item, com.p2ps.auth.model.Users user) {
        String itemName = item.getName();
        UserProductHistory history = historyRepository.findByUser_IdAndCustomNameIgnoreCase(user.getId(), itemName);
        if (history == null) {
            history = new UserProductHistory();
            history.setUser(user);
            history.setCustomName(itemName);
        }

        ProductCatalog catalogItem = item.getCatalogItem();

        // 1. Cautam in catalogul global prin fuzzy search
        if (catalogItem == null) {
            catalogItem = resolveCatalogByFuzzySearch(itemName);
        }

        // 2. Daca tot nu l-am gasit, INSEAMNA CA E NOU! Il cream noi acum!
        if (catalogItem == null) {
            String categoryToSave = (item.getCategory() != null && !item.getCategory().isBlank())
                    ? item.getCategory() : "Altele";

            catalogItem = catalogService.recordPurchase(
                    itemName,            // genericName
                    itemName,            // specificName
                    item.getBrand(),     // brand
                    categoryToSave,      // category
                    item.getPrice()      // price
            );

            // Atasam noul produs catalogat inapoi pe item
            item.setCatalogItem(catalogItem);
            logger.info("Created new global catalog entry for a purchased item.");
        }

        // 3. Salvam in history cu catalog_id-ul aferent
        if (catalogItem != null) {
            history.setCatalogItem(catalogItem);
        }

        history.setLastAddedTimestamp(System.currentTimeMillis());
        historyRepository.save(history);
    }

    /**
     * Performs a fuzzy search against the global product catalog (p2p_product_catalog)
     * using pg_trgm similarity matching. Returns the best match or null if nothing found.
     */
    private ProductCatalog resolveCatalogByFuzzySearch(String itemName) {
        List<ProductCatalog> matches = catalogRepository.searchByKeywordFuzzy(itemName);
        if (matches != null && !matches.isEmpty()) {
            ProductCatalog bestMatch = matches.get(0);
            logger.debug("Fuzzy catalog match for '{}': {} (id={})",
                    itemName, bestMatch.getSpecificName(), bestMatch.getId());
            return bestMatch;
        }
        logger.debug("No fuzzy catalog match found for '{}', leaving catalogId as null", itemName);
        return null;
    }
}
