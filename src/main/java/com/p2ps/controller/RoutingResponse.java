package com.p2ps.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutingResponse implements Serializable {

    private String status;
    private List<RoutePoint> route;
    private List<String> warnings;

    /**
     * BE 3.1 — Lazy Routing fields.
     *
     * routeId: present only for lazy responses. The frontend uses this to poll
     *          GET /api/routing/full/{routeId} for the 3-opt-optimized full route.
     *
     * partial: true  → this response contains only the first N stops (NN order).
     *          false → this response contains the full 3-opt-optimized route.
     */
    private String routeId;
    private boolean partial;

    /**
     * 3-arg constructor kept for backward compatibility with existing tests
     * (RoutingResponseTest, RoutingControllerTest).
     * Sets routeId=null and partial=false — equivalent to an eager response.
     */
    public RoutingResponse(String status, List<RoutePoint> route, List<String> warnings) {
        this.status = status;
        this.route = route;
        this.warnings = warnings;
        this.routeId = null;
        this.partial = false;
    }

    // ------------------------------------------------------------------
    // Factory methods — use these instead of constructors in new code
    // ------------------------------------------------------------------

    /** Full eager response: 3-opt done, no routeId needed. */
    public static RoutingResponse eager(List<RoutePoint> route, List<String> warnings) {
        return new RoutingResponse("success", route, warnings, null, false);
    }

    /** Partial lazy response: first N stops, full route computing in background. */
    public static RoutingResponse partial(String routeId, List<RoutePoint> partialRoute, List<String> warnings) {
        return new RoutingResponse("partial", partialRoute, warnings, routeId, true);
    }

    /** Full response retrieved from Redis after background optimization. */
    public static RoutingResponse full(String routeId, List<RoutePoint> route, List<String> warnings) {
        return new RoutingResponse("success", route, warnings, routeId, false);
    }

    /** Error response. */
    public static RoutingResponse error(String message) {
        return new RoutingResponse("error", List.of(), List.of(message), null, false);
    }
}
