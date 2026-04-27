package com.p2ps.lists.dto;

import com.p2ps.lists.model.ListCategory;
import lombok.Data;

@Data
public class UpdateListRequest {
    private String title;
    private ListCategory category;
    private String subcategory;
    private String finalStore;
}