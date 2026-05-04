package com.p2ps.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    private String routeId;
    private boolean partial;

    @JsonProperty("totalDistanceMeters")
    private double totalDistanceMeters;

    @JsonProperty("estimatedTimeSeconds")
    private int estimatedTimeSeconds;

    @JsonProperty("totalStops")
    private int totalStops;

    /**
     * 3-arg constructor kept for backward compatibility with existing tests
     */
    public RoutingResponse(String status, List<RoutePoint> route, List<String> warnings) {
        this.status = status;
        this.route = route;
        this.warnings = warnings;
    }

    // ------------------------------------------------------------------
    // Factory methods
    // ------------------------------------------------------------------

    public static RoutingResponse eager(List<RoutePoint> route, List<String> warnings) {
        return RoutingResponse.builder()
                .status("success")
                .route(route)
                .warnings(warnings)
                .build();
    }

    public static RoutingResponse partial(String routeId, List<RoutePoint> partialRoute, List<String> warnings) {
        return RoutingResponse.builder()
                .status("partial")
                .route(partialRoute)
                .warnings(warnings)
                .routeId(routeId)
                .partial(true)
                .build();
    }

    public static RoutingResponse full(String routeId, List<RoutePoint> route, List<String> warnings) {
        return RoutingResponse.builder()
                .status("success")
                .route(route)
                .warnings(warnings)
                .routeId(routeId)
                .build();
    }

    public static RoutingResponse error(String message) {
        return RoutingResponse.builder()
                .status("error")
                .route(List.of())
                .warnings(List.of(message))
                .build();
    }
}