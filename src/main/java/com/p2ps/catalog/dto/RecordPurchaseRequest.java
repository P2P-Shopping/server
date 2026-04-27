package com.p2ps.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordPurchaseRequest {
    private String genericName;
    
    @NotBlank(message = "Specific name is required")
    private String specificName;
    
    private String brand;
    
    private String category;
    
    private BigDecimal price;
}