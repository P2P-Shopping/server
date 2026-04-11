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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RoutingRequest that = (RoutingRequest) o;
        return Double.compare(getUserLat(), that.getUserLat()) == 0 && Double.compare(getUserLng(), that.getUserLng()) == 0 && Objects.equals(getProductIds(), that.getProductIds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserLat(), getUserLng(), getProductIds());
    }

    private double userLng;
    private List<String> productIds;


}
