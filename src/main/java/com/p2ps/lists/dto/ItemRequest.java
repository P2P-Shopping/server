package com.p2ps.lists.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMax;
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
    @DecimalMax(value = "999999999.99", message = "Price cannot exceed 999999999.99")
    private BigDecimal price;
    private String category;
    private Boolean isRecurrent;
    private Long timestamp;
}
