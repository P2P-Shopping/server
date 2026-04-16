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
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double lat;

    @NotNull(message = "Longitude is mandatory")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double lng;

    @NotNull(message = "Accuracy (in meters) is required")
    @Positive(message = "Accuracy (in meters) must be a positive number")
    private Double accuracyMeters;

    @NotNull(message = "Timestamp is mandatory")
    @Positive(message = "Timestamp must be a valid positive number")
    private Long timestamp;
}