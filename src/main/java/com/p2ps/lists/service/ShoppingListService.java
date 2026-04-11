package com.p2ps.lists.service;


import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.model.ShoppingList;
import com.p2ps.lists.repo.ShoppingListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final UserRepository userRepository;

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
        return shoppingListRepository.findByUser_Email(userEmail)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
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
