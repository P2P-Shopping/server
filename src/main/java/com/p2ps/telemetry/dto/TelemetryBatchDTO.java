package com.p2ps.telemetry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryBatchDTO {

    @NotEmpty(message = "The list of pings cannot be empty")
    @Valid
    private List<TelemetryPingDTO> pings;

}