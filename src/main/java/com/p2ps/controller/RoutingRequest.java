package com.p2ps.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRequest {

    private double userLat;
    private double userLng;
    private List<String> productIds;

    /**
     * BE 3.1 — Lazy Routing.
     *
     * 0 (default) = eager: compute and return the full optimized route immediately.
     * N > 0       = lazy:  return the first N stops immediately (NN order),
     *               kick off 3-opt in the background, include routeId in the response
     *               so the frontend can poll GET /api/routing/full/{routeId}.
     *
     * Recommended value for large stores: 5.
     * Only activates lazy behaviour when the store has more than N products.
     */
    private int lazyN = 0;
}
