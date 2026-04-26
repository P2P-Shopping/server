package com.p2ps.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RoutingRequest that = (RoutingRequest) o;
        return Double.compare(getUserLat(), that.getUserLat()) == 0
                && Double.compare(getUserLng(), that.getUserLng()) == 0
                && Objects.equals(getProductIds(), that.getProductIds())
                && getLazyN() == that.getLazyN();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserLat(), getUserLng(), getProductIds(), getLazyN());
    }

    public double getUserLat() { return this.userLat; }
    public void setUserLat(double userLat) { this.userLat = userLat; }

    public double getUserLng() { return this.userLng; }
    public void setUserLng(double userLng) { this.userLng = userLng; }

    public List<String> getProductIds() { return this.productIds; }
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }

    public int getLazyN() { return this.lazyN; }
    public void setLazyN(int lazyN) { this.lazyN = lazyN; }
}
