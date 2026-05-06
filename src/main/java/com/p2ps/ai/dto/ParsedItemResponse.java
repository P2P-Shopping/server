package com.p2ps.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ParsedItemResponse {
   private String genericName;  // ex: lapte
   private String specificName; // ex: Lapte Zuzu 1.5%
   private String brand;        // ex: Zuzu
   private String quantity;
   private String unit;
   private String catalogId;      // ID-ul din baza de date catalog
   private String category;     // ex: Lactate
   private BigDecimal price;
}
