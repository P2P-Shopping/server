package com.p2ps.ai.service;

public record CatalogStandardizationResult(
        String cleanName,
        String category,
        String brand,
        String defaultQuantity
) {
}
