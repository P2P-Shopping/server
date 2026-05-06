package com.p2ps.catalog.dto;

import java.math.BigDecimal;

public record ProductSuggestionDTO(
        String name,
        String brand,
        String category,
        BigDecimal price,
        String quantity
) {}