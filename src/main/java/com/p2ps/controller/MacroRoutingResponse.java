package com.p2ps.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BE 3.2 — response for GET /api/routing/macro
 *
 * Both walking and driving can be null independently if OSRM fails for that profile.
 * The polyline field contains an Encoded Polyline string that the frontend decodes
 * to draw the actual road route on a map (not a straight line).
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
        /** Road distance in metres. */
        private double distanceM;
        /** Estimated travel time in seconds. */
        private double durationSeconds;
        /**
         * Encoded Polyline string — represents all turns and curves of the route on real roads.
         * Frontend decodes this using a polyline library (e.g. @mapbox/polyline) to draw the route.
         * Null if OSRM did not return geometry.
         */
        private String polyline;
    }
}
