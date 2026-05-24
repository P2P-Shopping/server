package com.p2ps.controller;

public record StoreMatchResponse(
        String storeId,
        String storeName,
        int matchedItems,
        double distanceMeters,
        long matchPercentage,
        java.math.BigDecimal totalEstimatedPrice,
        int pricedItems,
        long priceCoveragePercentage
) {
}


