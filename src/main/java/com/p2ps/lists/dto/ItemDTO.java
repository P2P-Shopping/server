package com.p2ps.lists.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ItemDTO {
    private UUID id;
    private String name;
    @com.fasterxml.jackson.annotation.JsonProperty("isChecked")
    private boolean isChecked;
    private String brand;
    private String quantity;
    private BigDecimal price;
    private String category;
    private boolean isRecurrent;
    private Long lastUpdatedTimestamp;
}