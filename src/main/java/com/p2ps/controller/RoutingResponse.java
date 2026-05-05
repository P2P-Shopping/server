package com.p2ps.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class RoutingResponse {

    private String status;
    private List<RoutePoint> route;
    private String routeId;
    private boolean partial;
    private List<String> warnings;

    // --- REPARAȚIA AICI: Am restaurat formatul snake_case pentru API-ul public ---

    @JsonProperty("total_distance_meters")
    private double totalDistanceMeters;

    @JsonProperty("estimated_time_seconds")
    private long estimatedTimeSeconds;

    @JsonProperty("total_stops")
    private int totalStops;

    // Constructor
    public RoutingResponse() {
        this.route = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    // --- Getters & Setters ---

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<RoutePoint> getRoute() {
        return route;
    }

    public void setRoute(List<RoutePoint> route) {
        this.route = route;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public boolean isPartial() {
        return partial;
    }

    public void setPartial(boolean partial) {
        this.partial = partial;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public void setTotalDistanceMeters(double totalDistanceMeters) {
        this.totalDistanceMeters = totalDistanceMeters;
    }

    public long getEstimatedTimeSeconds() {
        return estimatedTimeSeconds;
    }

    public void setEstimatedTimeSeconds(long estimatedTimeSeconds) {
        this.estimatedTimeSeconds = estimatedTimeSeconds;
    }

    public int getTotalStops() {
        return totalStops;
    }

    public void setTotalStops(int totalStops) {
        this.totalStops = totalStops;
    }
}