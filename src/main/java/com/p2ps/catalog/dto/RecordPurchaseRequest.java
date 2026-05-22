package com.p2ps.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RecordPurchaseRequest {
    private UUID storeId;

    private String genericName;
    
    @NotBlank(message = "Specific name is required")
    private String specificName;
    
    private String brand;
    
    private String category;
    
    private BigDecimal price;
}
