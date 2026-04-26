package com.p2ps.lists.controller;

import com.p2ps.lists.dto.CreateListRequest;
import com.p2ps.lists.dto.ImportItemsRequestDTO;
import com.p2ps.lists.dto.ShoppingListDTO;
import com.p2ps.lists.dto.UpdateListRequest;
import com.p2ps.lists.service.ShoppingListService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lists")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    @PostMapping
    public ResponseEntity<ShoppingListDTO> createList(
            @Valid @RequestBody CreateListRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        ShoppingListDTO createdList = shoppingListService.createList(
                request.getTitle(), userEmail, request.getCategory(), request.getSubcategory());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdList);
    }
    
    @PatchMapping("/{listId}")
    public ResponseEntity<ShoppingListDTO> updateList(
            @PathVariable UUID listId,
            @RequestBody UpdateListRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        
        ShoppingListDTO updateDto = new ShoppingListDTO();
        updateDto.setTitle(request.getTitle());
        updateDto.setCategory(request.getCategory());
        updateDto.setSubcategory(request.getSubcategory());
        updateDto.setFinalStore(request.getFinalStore());
        
        ShoppingListDTO updatedList = shoppingListService.updateList(listId, updateDto, userEmail);
        return ResponseEntity.ok(updatedList);
    }

    @GetMapping
    public ResponseEntity<List<ShoppingListDTO>> getMyLists(Authentication authentication) {

        String userEmail = authentication.getName();
        List<ShoppingListDTO> myLists = shoppingListService.getUserLists(userEmail);

        return ResponseEntity.ok(myLists);
    }

    @GetMapping("/{listId}")
    public ResponseEntity<ShoppingListDTO> getList(
            @PathVariable UUID listId,
            Authentication authentication) {
        ShoppingListDTO list = shoppingListService.getListById(listId, authentication.getName());
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/{listId}")
    public ResponseEntity<Void> deleteList(
            @PathVariable UUID listId,
            Authentication authentication) {
        shoppingListService.deleteList(listId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{currentListId}/import")
    public ResponseEntity<ShoppingListDTO> importItems(
            @PathVariable UUID currentListId,
            @RequestBody ImportItemsRequestDTO request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        ShoppingListDTO updatedList = shoppingListService.importItems(currentListId, request, userEmail);
        return ResponseEntity.ok(updatedList);
    }
}