package com.p2ps.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingResponse implements Serializable {

    private String status;
    private List<RoutePoint> route;
    private List<String> warnings;

    /**
     * BE 3.1 — Lazy Routing fields.
     * <p>
     * routeId: present only for lazy responses. The frontend uses this to poll
     *          GET /api/routing/full/{routeId} for the 3-opt-optimized full route.
     * <p>
     * partial: true  -> this response contains only the first N stops (NN order).
     *          false -> this response contains the full 3-opt-optimized route.
     */
    private String routeId;
    private boolean partial;

    // --- Performance Metrics ---
    private double totalDistanceMeters;
    private int estimatedTimeSeconds;
    @com.fasterxml.jackson.annotation.JsonProperty("total_distance_meters")
    private double totalDistanceMeters;
    @com.fasterxml.jackson.annotation.JsonProperty("estimated_time_seconds")
    private int estimatedTimeSeconds;
    @com.fasterxml.jackson.annotation.JsonProperty("total_stops")
    private int totalStops;

    /**
     * 3-arg constructor kept for backward compatibility with existing tests
     * (RoutingResponseTest, RoutingControllerTest).
     * Equivalent to an eager response.
     */
    public RoutingResponse(String status, List<RoutePoint> route, List<String> warnings) {
        this.status = status;
        this.route = route;
        this.warnings = warnings;
        // Primitive fields (totalDistanceMeters, estimatedTimeSeconds, totalStops)
        // and boolean (partial) default to 0/false automatically in Java.
        this.routeId = null;
        this.partial = false;
        this.totalDistanceMeters = 0.0;
        this.estimatedTimeSeconds = 0;
        this.totalStops = 0;
    }

    // ------------------------------------------------------------------
    // Factory methods
    // ------------------------------------------------------------------

    /** Full eager response: 3-opt done, no routeId needed. */
    public static RoutingResponse eager(List<RoutePoint> route, List<String> warnings) {
        return RoutingResponse.builder()
                .status("success")
                .route(route)
                .warnings(warnings)
                .build();
        return new RoutingResponse("success", route, warnings, null, false, 0.0, 0, 0);
    }

    /** Partial lazy response: first N stops, full route computing in background. */
    public static RoutingResponse partial(String routeId, List<RoutePoint> partialRoute, List<String> warnings) {
        return RoutingResponse.builder()
                .status("partial")
                .route(partialRoute)
                .warnings(warnings)
                .routeId(routeId)
                .partial(true)
                .build();
        return new RoutingResponse("partial", partialRoute, warnings, routeId, true, 0.0, 0, 0);
    }

    /** Full response retrieved from Redis after background optimization. */
    public static RoutingResponse full(String routeId, List<RoutePoint> route, List<String> warnings) {
        return RoutingResponse.builder()
                .status("success")
                .route(route)
                .warnings(warnings)
                .routeId(routeId)
                .build();
        return new RoutingResponse("success", route, warnings, routeId, false, 0.0, 0, 0);
    }

    /** Error response. */
    public static RoutingResponse error(String message) {
        return RoutingResponse.builder()
                .status("error")
                .route(List.of())
                .warnings(List.of(message))
                .build();
        return new RoutingResponse("error", List.of(), List.of(message), null, false, 0.0, 0, 0);
    }
}