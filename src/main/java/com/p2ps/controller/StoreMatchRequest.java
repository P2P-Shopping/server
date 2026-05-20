package com.p2ps.controller;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class StoreMatchRequest {
    private double userLat;
    private double userLng;
    private double radiusInMeters;
    private List<UUID> itemIds;
    private List<String> itemNames;
}