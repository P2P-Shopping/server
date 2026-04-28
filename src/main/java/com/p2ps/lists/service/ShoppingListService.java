package com.p2ps.lists.service;


import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.lists.dto.ImportItemsRequestDTO;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ListCategory;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ItemRepository;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final UserRepository userRepository;
    private static final String SHOPPING_LIST_NOT_FOUND = "Shopping list not found";
    private final ItemRepository itemRepository;

    public ShoppingListService(ShoppingListRepository shoppingListRepository, UserRepository userRepository, ItemRepository itemRepository) {
        this.shoppingListRepository = shoppingListRepository;
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public ShoppingListDTO createList(String title, String userEmail, ListCategory category, String subcategory) {
        //userul curent pe baza emailului din JWT
        Users currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ListUserNotFoundException("User not found"));

        ShoppingList newList = new ShoppingList();
        newList.setTitle(title);
        newList.setUser(currentUser);
        if (category != null) {
            newList.setCategory(category);
        }
        newList.setSubcategory(subcategory);
        ShoppingList savedList = shoppingListRepository.save(newList);

        return mapToDTO(savedList);
    }

    @Transactional
    public ShoppingListDTO updateList(UUID listId, ShoppingListDTO updateDto, String userEmail) {
        ShoppingList list = getListEntityByIdAndUser(listId, userEmail);

        if (updateDto.getTitle() != null) {
            list.setTitle(updateDto.getTitle());
        }
        if (updateDto.getCategory() != null) {
            list.setCategory(updateDto.getCategory());
        }
        // Permite resetarea valorilor optionale (subcategory si finalStore) doar
        // daca se trimit in DTO. Intrucat `UpdateListRequest` e JSON, DTO-ul ar putea sa foloseasca
        // JsonNullable pt a distinge intre explicit null si camp lipsa, dar
        // momentan vom updata doar daca valoarea nu e null, as-is din cerinta sau
        // lasam asa cum e (se va accepta ca null in DTO nu face update la acele campuri)
        if (updateDto.getSubcategory() != null) {
            list.setSubcategory(updateDto.getSubcategory().isEmpty() ? null : updateDto.getSubcategory());
        }
        if (updateDto.getFinalStore() != null) {
            list.setFinalStore(updateDto.getFinalStore().isEmpty() ? null : updateDto.getFinalStore());
        }

        ShoppingList savedList = shoppingListRepository.save(list);
        return mapToDTO(savedList);
    }

    @Transactional(readOnly = true)
    public List<ShoppingListDTO> getUserLists(String userEmail) {
        return shoppingListRepository.findAccessibleByEmail(userEmail)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional
    public void deleteList(java.util.UUID listId, String userEmail) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (!list.getUser().getEmail().equals(userEmail)) {
            throw new ListAccessDeniedException("Only the owner can delete this list");
        }

        shoppingListRepository.delete(list);
    }

    @Transactional(readOnly = true)
    public ShoppingListDTO getListById(java.util.UUID listId, String userEmail) {
        ShoppingList list = getListEntityByIdAndUser(listId, userEmail);
        return mapToDTO(list);
    }

    @Transactional
    public ShoppingListDTO importItems(UUID currentListId, ImportItemsRequestDTO request, String userEmail) {
        if (request.getSourceListId() == null) {
            throw new IllegalArgumentException("Source list ID cannot be null");
        }

        if (currentListId.equals(request.getSourceListId())) {
            throw new IllegalArgumentException("Cannot import items from the same list into itself");
        }

        ShoppingList currentList = getListEntityByIdAndUser(currentListId, userEmail);
        ShoppingList sourceList = getListEntityByIdAndUser(request.getSourceListId(), userEmail);

        List<Item> itemsToImport = sourceList.getItems();

        if (request.getItemIds() != null && !request.getItemIds().isEmpty()) {
            itemsToImport = itemsToImport.stream()
                .filter(item -> request.getItemIds().contains(item.getId()))
                .toList();
        }

        for (Item item : itemsToImport) {
            Item newItem = new Item();
            newItem.setName(item.getName());
            newItem.setBrand(item.getBrand());
            newItem.setQuantity(item.getQuantity());
            newItem.setPrice(item.getPrice());
            newItem.setCategory(item.getCategory());
            newItem.setRecurrent(item.isRecurrent());
            newItem.setShoppingList(currentList);
            newItem.setLastUpdatedTimestamp(System.currentTimeMillis());

            itemRepository.save(newItem);
            currentList.getItems().add(newItem);
        }

        return mapToDTO(shoppingListRepository.save(currentList));
    }

    private ShoppingList getListEntityByIdAndUser(UUID listId, String userEmail) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        boolean isOwner = list.getUser().getEmail().equals(userEmail);
        boolean isCollaborator = list.getCollaborators().stream()
                .anyMatch(c -> c.getEmail().equals(userEmail));

        if (!isOwner && !isCollaborator) {
            throw new ListAccessDeniedException("You do not have permission to view this list");
        }
        return list;
    }

    @Transactional
    public void shareList(java.util.UUID listId, String collaboratorEmail, String ownerEmail) {
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        if (!list.getUser().getEmail().equals(ownerEmail)) {
            throw new ListAccessDeniedException("Only the owner can share this list");
        }

        if (collaboratorEmail.equals(ownerEmail)) {
            throw new IllegalArgumentException("Cannot share list with owner");
        }

        Users collaborator = userRepository.findByEmail(collaboratorEmail)
                .orElseThrow(() -> new ListUserNotFoundException("Collaborator user not found"));

        list.getCollaborators().add(collaborator);
        shoppingListRepository.save(list);
    }

    private ShoppingListDTO mapToDTO(ShoppingList list) {
        ShoppingListDTO dto = new ShoppingListDTO();
        dto.setId(list.getId());
        dto.setTitle(list.getTitle());
        dto.setCategory(list.getCategory());
        dto.setSubcategory(list.getSubcategory());
        dto.setFinalStore(list.getFinalStore());
        if (list.getUser() != null) {
            dto.setUserId(list.getUser().getId().toString());
            dto.setOwnerEmail(list.getUser().getEmail());
            String fullName = list.getUser().getFirstName() + " " + list.getUser().getLastName();
            dto.setOwnerName(fullName.trim());
        }

        if (list.getItems() != null) {
            dto.setItems(list.getItems().stream()
                    .map(item -> {
                        ItemDTO itemDto = new ItemDTO();
                        itemDto.setId(item.getId());
                        itemDto.setName(item.getName());
                        itemDto.setChecked(item.isChecked());
                        itemDto.setBrand(item.getBrand());
                        itemDto.setPrice(item.getPrice());
                        itemDto.setQuantity(item.getQuantity());
                        itemDto.setCategory(item.getCategory());
                        itemDto.setRecurrent(item.isRecurrent());
                        itemDto.setLastUpdatedTimestamp(item.getLastUpdatedTimestamp());
                        return itemDto;
                    })
                    .toList());
        } else {
            dto.setItems(new ArrayList<>());
        }

        return dto;
    }
}
