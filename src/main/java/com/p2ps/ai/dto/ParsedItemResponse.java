package com.p2ps.ai.dto;

import lombok.Data;

@Data
public class ParsedItemResponse {
   private String genericName;  // ex: lapte
   private String specificName; // ex: Lapte Zuzu 1.5%
   private String brand;        // ex: Zuzu
   private Double quantity;
   private String unit;
   private String catalogId;      // ID-ul din baza de date catalog
   private String category;     // ex: Lactate
}
