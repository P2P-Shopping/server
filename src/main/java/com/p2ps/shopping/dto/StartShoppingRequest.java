package com.p2ps.shopping.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class StartShoppingRequest {
    private UUID listId;
    private UUID storeId;
    private String customStoreName;
    private String customStoreAddress;
    private String customStoreNotes;
}
