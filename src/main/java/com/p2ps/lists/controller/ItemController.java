package com.p2ps.lists.controller;


import com.p2ps.lists.dto.ItemDTO;
import com.p2ps.lists.dto.ItemRequest;
import com.p2ps.lists.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping("/lists/{listId}/items")
    public ResponseEntity<ItemDTO> addItem(
            @PathVariable UUID listId,
            @Valid @RequestBody ItemRequest request,
            Authentication authentication) {

        ItemDTO createdItem = itemService.addItemToList(listId, request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(createdItem);
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ItemDTO> updateItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemRequest request,
            Authentication authentication) {

        ItemDTO updatedItem = itemService.updateItem(itemId, request, authentication.getName());
        return ResponseEntity.ok(updatedItem);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable UUID itemId,
            Authentication authentication) {

        itemService.deleteItem(itemId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}