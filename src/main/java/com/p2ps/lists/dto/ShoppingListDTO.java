package com.p2ps.lists.dto;

import com.p2ps.lists.model.ListCategory;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ShoppingListDTO {
    private UUID id;
    private String title;
    private ListCategory category;
    private String subcategory;
    private String finalStore;
    private List<ItemDTO> items;
    private String ownerName;
    private String ownerEmail;
    private String userId;
}