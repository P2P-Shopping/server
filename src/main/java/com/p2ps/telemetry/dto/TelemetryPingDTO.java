package com.p2ps.telemetry.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TelemetryPingDTO {

    @NotBlank(message = "Device ID is mandatory and cannot be blank")
    private String deviceId;

    @NotBlank(message = "Store ID is mandatory and cannot be blank")
    private String storeId;

    @NotBlank(message = "Item ID is mandatory and cannot be blank")
    private String itemId;

    @NotNull(message = "Latitude is mandatory")
    @Min(value = -90, message = "Minimum valid latitude is -90")
    @Max(value = 90, message = "Maximum valid latitude is 90")
    private Double lat;

    @NotNull(message = "Longitude is mandatory")
    @Min(value = -180, message = "Minimum valid longitude is -180")
    @Max(value = 180, message = "Maximum valid longitude is 180")
    private Double lng;

    @PositiveOrZero(message = "Accuracy (in meters) must be zero or a positive number")
    private Double accuracyMeters;

    @NotNull(message = "Timestamp is mandatory")
    private Long timestamp;
}