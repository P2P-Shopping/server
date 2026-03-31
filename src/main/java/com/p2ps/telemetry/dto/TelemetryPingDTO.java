package com.p2ps.telemetry.dto;

import lombok.Data;

@Data
public class TelemetryPingDTO {
    private String deviceId;
    private String storeId;
    private String itemId;
    private Double lat;
    private Double lng;
    private Double accuracyMeters;
    private Long timestamp;
}