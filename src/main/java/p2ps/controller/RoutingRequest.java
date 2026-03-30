package p2ps.controller;

import java.util.List;

public class RoutingRequest {
    private double userLat;
    private double userLng;
    private List<String> productIds;

    public RoutingRequest() {}
    public RoutingRequest(double userLat, double userLng, List<String> productIds) {
        this.userLat = userLat;
        this.userLng = userLng;
        this.productIds = productIds;
    }

    //getters
    public double getUserLat() { return userLat; }
    public double getUserLng() { return userLng; }
    public List<String> getProductIds() { return productIds; }

    //setters
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }
    public void setUserLat(double userLat) { this.userLat = userLat; }
    public void setUserLng(double userLng) { this.userLng = userLng; }
}
