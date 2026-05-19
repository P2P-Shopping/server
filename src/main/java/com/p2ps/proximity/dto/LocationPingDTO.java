package com.p2ps.proximity.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * DTO for background location pings sent by the Android app.
 * Unlike TelemetryPingDTO, this does not require a storeId or itemId
 * because the user is not necessarily inside a store.
 */
@Data
public class LocationPingDTO {

    @NotBlank(message = "Device ID is mandatory and cannot be blank")
    private String deviceId;

    @NotNull(message = "Latitude is mandatory")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double lat;

    @NotNull(message = "Longitude is mandatory")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double lng;

    @NotNull(message = "Timestamp is mandatory")
    @Positive(message = "Timestamp must be a valid positive number")
    private Long timestamp;

    /** FCM token used to push a notification back to this specific device. */
    @NotBlank(message = "FCM token is mandatory")
    private String fcmToken;
}