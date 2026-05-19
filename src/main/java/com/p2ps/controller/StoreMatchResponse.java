package com.p2ps.controller;

public record StoreMatchResponse(
        String storeId,
        String storeName,
        int matchedItems,
        double distanceMeters,
        int matchPercentage
) {
}


