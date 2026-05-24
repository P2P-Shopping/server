package com.p2ps.lists.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    private Double positionIndex;
    private Long timestamp;

    @DecimalMin(value = "-90.0", message = "Latitude must be at least -90.0")
    @DecimalMax(value = "90.0", message = "Latitude cannot exceed 90.0")
    private Double lat;

    @DecimalMin(value = "-180.0", message = "Longitude must be at least -180.0")
    @DecimalMax(value = "180.0", message = "Longitude cannot exceed 180.0")
    private Double lng;

    @DecimalMin(value = "0.0", message = "Accuracy must be zero or positive")
    private Double accuracyMeters;
}
