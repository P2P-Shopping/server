package com.p2ps.ai.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiGenerationResponse {
    // AI list type: "RECIPE", "FREQUENT" or "NORMAL"
    private String listType;

    // List of parsed items
    private List<ParsedItemResponse> items;
}