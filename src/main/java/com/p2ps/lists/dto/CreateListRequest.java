package com.p2ps.lists.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateListRequest {
    @NotBlank(message = "Title cannot be empty")
    private String title;
}