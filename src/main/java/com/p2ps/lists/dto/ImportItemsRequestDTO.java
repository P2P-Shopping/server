package com.p2ps.lists.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ImportItemsRequestDTO {
    @NotNull(message = "Source list ID cannot be null")
    private UUID sourceListId;
    private List<UUID> itemIds; // Optional: if null/empty, import all items
}