package com.p2ps.lists.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ImportItemsRequestDTO {
    private UUID sourceListId;
    private List<UUID> itemIds; // Optional: if null/empty, import all items
}