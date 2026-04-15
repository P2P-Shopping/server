package com.p2ps.telemetry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryBatchDTO {

    @NotNull(message = "The list of pings cannot be null")
    @Size(min = 1, max = 1000, message = "The batch must contain between 1 and 1000 pings")
    @Valid
    private List<@NotNull @Valid TelemetryPingDTO> pings;

}
