package com.p2ps.lists.service;


import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final UserRepository userRepository;
    private static final String SHOPPING_LIST_NOT_FOUND = "Shopping list not found";

    public ShoppingListService(ShoppingListRepository shoppingListRepository, UserRepository userRepository) {
        this.shoppingListRepository = shoppingListRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ShoppingListDTO createList(String title, String userEmail) {
        //userul curent pe baza emailului din JWT
        Users currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ListUserNotFoundException("User not found"));

        ShoppingList newList = new ShoppingList();
        newList.setTitle(title);
        newList.setUser(currentUser);

        ShoppingList savedList = shoppingListRepository.save(newList);

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
        ShoppingList list = shoppingListRepository.findById(listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(SHOPPING_LIST_NOT_FOUND));

        boolean isOwner = list.getUser().getEmail().equals(userEmail);
        boolean isCollaborator = list.getCollaborators().stream()
                .anyMatch(c -> c.getEmail().equals(userEmail));

        if (!isOwner && !isCollaborator) {
            throw new ListAccessDeniedException("You do not have permission to view this list");
        }

        return mapToDTO(list);
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
