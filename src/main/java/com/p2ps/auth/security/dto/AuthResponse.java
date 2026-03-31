package com.p2ps.auth.security.dto;


import lombok.Getter;

@Getter
public class AuthResponse {
    private String token;

    public AuthResponse(String token) { this.token = token; }

}