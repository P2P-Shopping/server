package com.p2ps.controller;

import com.p2ps.service.RoutingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/calculate")
    public RoutingResponse calculateRoute(@RequestBody RoutingRequest request) {
        return routingService.calculateOptimalRoute(request);
    }
}