package com.p2ps.controller;

import java.util.List;

public class RoutingResponse {
    private String status;
    private List<RoutePoint> route;

    public RoutingResponse(String status, List<RoutePoint> route) {
        this.status = status;
        this.route = route;
    }

    //getters
    public String getStatus() { return status; }
    public List<RoutePoint> getRoute() { return route; }

    //setters
    public void setStatus(String status) { this.status = status; }
    public void setRoute(List<RoutePoint> route) { this.route = route; }
}
