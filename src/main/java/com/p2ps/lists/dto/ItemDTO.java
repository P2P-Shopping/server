package com.p2ps.lists.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ItemDTO {
    private UUID id;
    private String name;
    private boolean isChecked;

    @com.fasterxml.jackson.annotation.JsonProperty("isChecked")
    public boolean isChecked() {
        return isChecked;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isChecked")
    public void setChecked(boolean isChecked) {
        this.isChecked = isChecked;
    }
    private String brand;
    private String quantity;
    private BigDecimal price;
    private String category;
    private boolean isRecurrent;
    private Long lastUpdatedTimestamp;
}