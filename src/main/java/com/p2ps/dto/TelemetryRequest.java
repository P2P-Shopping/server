package com.p2ps.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

/**
 * Obiect de transfer pentru datele de telemetrie trimise de aplicația mobilă.
 * Include validări pentru a asigura integritatea coordonatelor GPS și a acurateței.
 */
public class TelemetryRequest {

    @NotNull(message = "Store ID is required")
    private UUID storeId;

    @NotNull(message = "Item ID is required")
    private UUID itemId;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90 degrees")
    private Double lat;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180 degrees")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180 degrees")
    private Double lon;

    @NotNull(message = "Accuracy is required")
    @Min(value = 0, message = "Accuracy cannot be negative")
    @Max(value = 100, message = "Accuracy above 100m is discarded as noise")
    private Double accuracy;

    // --- Getters și Setters ---

    public UUID getStoreId() {
        return storeId;
    }

    public void setStoreId(UUID storeId) {
        this.storeId = storeId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }
    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }
}