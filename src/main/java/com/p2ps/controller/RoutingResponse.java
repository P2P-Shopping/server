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
}
