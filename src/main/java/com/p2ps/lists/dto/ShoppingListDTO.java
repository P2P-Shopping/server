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
    private UUID finalStoreId;
    private String finalStoreName;
    private List<ItemDTO> items;
    private Integer ownerId;
    private List<CollaboratorDTO> collaborators;
    private String currentUserRole;
    private String ownerName;
    private String ownerEmail;
    private String userId;

    public String getFinalStore() {
        return finalStoreName;
    }

    public void setFinalStore(String finalStore) {
        this.finalStoreName = finalStore;
    }
}
