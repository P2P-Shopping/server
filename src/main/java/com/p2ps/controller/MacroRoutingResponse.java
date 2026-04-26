package com.p2ps.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BE 3.2 — response for GET /api/routing/macro
 *
 * Both walking and driving can be null independently if OSRM fails for that profile.
 * The frontend checks for null before rendering — this is intentional: a null estimate
 * means "unavailable" (e.g. no walking path exists), not an error.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroRoutingResponse {

    /** Walking estimate from user location to store entrance. Null if OSRM unavailable. */
    private TransportEstimate walking;

    /** Driving estimate from user location to store entrance. Null if OSRM unavailable. */
    private TransportEstimate driving;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransportEstimate {
        /** Straight-road distance in metres. */
        private double distanceM;
        /** Estimated travel time in seconds. */
        private double durationSeconds;
    }
}
