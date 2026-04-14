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

    // Provide explicit getters/setters to ensure Lombok-less environments (and IDEs) see them
    public double getUserLat() { return this.userLat; }
    public void setUserLat(double userLat) { this.userLat = userLat; }

    public double getUserLng() { return this.userLng; }
    public void setUserLng(double userLng) { this.userLng = userLng; }

    public List<String> getProductIds() { return this.productIds; }
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }


}
