package com.p2ps.controller;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class RoutePoint implements Serializable {

    private String itemId;
    private String name;
    private double lat;
    private double lng;

    /**
     * UI hint for the frontend so it can render each stop differently.
     * <ul>
     *   <li>{@code "USER"}     — the shopper's current position (first node)</li>
     *   <li>{@code "PRODUCT"}  — a product stop (default for the 4-arg constructor)</li>
     *   <li>{@code "CHECKOUT"} — the checkout / exit point (last node, issue #154)</li>
     * </ul>
     */
    private String type;

    /**
     * Backward-compatible constructor — keeps all existing call-sites unchanged.
     * Type defaults to "PRODUCT".
     */
    public RoutePoint(String itemId, String name, double lat, double lng) {
        this(itemId, name, lat, lng, "PRODUCT");
    }

    /** Full constructor used when the type is known (USER / CHECKOUT). */
    public RoutePoint(String itemId, String name, double lat, double lng, String type) {
        this.itemId = itemId;
        this.name   = name;
        this.lat    = lat;
        this.lng    = lng;
        this.type   = type;
    }
}
