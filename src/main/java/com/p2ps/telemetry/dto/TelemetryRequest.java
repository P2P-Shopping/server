package com.p2ps.telemetry.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class TelemetryRequest {
    @NotNull
    private UUID storeId;

    @NotNull
    private UUID itemId;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double lat;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double lon;

    @NotNull
    @Positive
    private Double accuracy;
}
