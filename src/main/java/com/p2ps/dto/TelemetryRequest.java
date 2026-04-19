package com.p2ps.dto;

import java.util.UUID;

public class TelemetryRequest {
    private UUID storeId;
    private UUID itemId;
    private Double lat;      // Schimbat din double în Double
    private Double lon;      // Schimbat din double în Double
    private Double accuracy; // Schimbat din double în Double

    // Getters și Setters neschimbați, dar returnează/primesc acum Double
    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
}