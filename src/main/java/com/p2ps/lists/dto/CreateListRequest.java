package com.p2ps.lists.dto;

import com.p2ps.lists.model.ListCategory;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateListRequest {
    @NotBlank(message = "Title cannot be empty")
    private String title;
    
    private ListCategory category;
    private String subcategory;
}