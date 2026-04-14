package com.p2ps.lists.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemRequest {
    private String name;
    private String brand;
    private String quantity;
    private BigDecimal price;
    private String category;
    private Boolean isRecurrent;
    private Boolean isChecked;
}
