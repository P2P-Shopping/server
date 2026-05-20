package com.p2ps.lists.dto;

import lombok.Data;

@Data
public class CollaboratorDTO {
    private Integer userId;
    private String email;
    private String name;
    private String role;
}
