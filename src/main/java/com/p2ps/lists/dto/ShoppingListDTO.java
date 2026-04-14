package com.p2ps.lists.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ShoppingListDTO {
    private UUID id;
    private String title;
    private List<ItemDTO> items;
}