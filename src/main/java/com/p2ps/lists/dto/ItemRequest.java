package com.p2ps.lists.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ItemRequest {

    private String name;
    @JsonProperty("isChecked")
    private Boolean isChecked;
    private String brand;
    private String quantity;
    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;
    private String category;
    private Boolean isRecurrent;
    private Long timestamp;
}
