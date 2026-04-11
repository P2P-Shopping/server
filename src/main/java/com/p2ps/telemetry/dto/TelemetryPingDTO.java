package com.p2ps.telemetry.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TelemetryPingDTO {

    @NotBlank(message = "deviceId is required")
    private String deviceId;

    @NotBlank(message = "storeId is required")
    private String storeId;

    @NotBlank(message = "itemId is required")
    private String itemId;

    @NotNull(message = "lat is required")
    @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
    private Double lat;

    @NotNull(message = "lng is required")
    @DecimalMin(value = "-180.0", message = "lng must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "lng must be between -180 and 180")
    private Double lng;

    @NotNull(message = "accuracyMeters is required")
    @Positive(message = "accuracyMeters must be positive")
    private Double accuracyMeters;

    @NotNull(message = "timestamp is required")
    @Positive(message = "timestamp must be a valid positive number")
    private Long timestamp;
}