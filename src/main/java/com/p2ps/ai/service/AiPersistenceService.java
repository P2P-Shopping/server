package com.p2ps.ai.service;

import com.p2ps.ai.dto.ParsedItemResponse;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.service.ItemService;
import com.p2ps.lists.service.ShoppingListService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiPersistenceService {

    private final ShoppingListService shoppingListService;
    private final ItemService itemService;

    public AiPersistenceService(ShoppingListService shoppingListService, ItemService itemService) {
        this.shoppingListService = shoppingListService;
        this.itemService = itemService;
    }

    @Transactional
    public void createListAndPopulateItems(UUID targetListId, String newListTitle, List<ParsedItemResponse> validItems, String userEmail) {
        if (targetListId == null) {
            String title = (newListTitle != null && !newListTitle.isBlank())
                    ? newListTitle
                    : "AI Generated " + java.time.LocalDate.now();
            ShoppingListDTO newList = shoppingListService.createList(title, userEmail);
            targetListId = newList.getId();
        }

        List<ItemRequest> batchItems = new ArrayList<>();
        for (ParsedItemResponse aiItem : validItems) {
            ItemRequest newItem = new ItemRequest();
            newItem.setName(aiItem.getName().trim());
            String quantityStr = (aiItem.getQuantity() != null ? String.valueOf(aiItem.getQuantity()) : "");
            String unitStr = (aiItem.getUnit() != null ? aiItem.getUnit() : "");
            newItem.setQuantity((quantityStr + " " + unitStr).trim());
            newItem.setCategory("AI Generated");
            batchItems.add(newItem);
        }

        if (!batchItems.isEmpty()) {
            itemService.addItemsToList(targetListId, batchItems, userEmail);
        }
    }
}