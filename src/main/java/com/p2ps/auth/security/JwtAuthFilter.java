package com.p2ps.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final com.p2ps.auth.service.UserService userService;

    public JwtAuthFilter(JwtUtil jwtUtil, @org.springframework.context.annotation.Lazy com.p2ps.auth.service.UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        String token = authorizationHeader.trim();

        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return token.substring(7).trim();
        }

        return token;
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt-token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public UsernamePasswordAuthenticationToken authenticateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            String userEmail = jwtUtil.extractEmail(token);
            Integer tokenVersion = jwtUtil.extractTokenVersion(token);

            if (userEmail != null && tokenVersion != null && !jwtUtil.isTokenExpired(token)) {
                return userService.findByEmail(userEmail)
                        .filter(user -> tokenVersion.equals(user.getTokenVersion()))
                        .map(user -> new UsernamePasswordAuthenticationToken(userEmail, null, new ArrayList<>()))
                        .orElse(null);
            }
        } catch (Exception _) {
            return null;
        }

        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // incercam sa extragem tokenul din cookie mai intai
        String token = extractTokenFromCookie(request);

        //  daca nu e in cookie, verificam headerul
        if (token == null|| token.isBlank()) {
            token = extractBearerToken(request.getHeader("Authorization"));
        }


        UsernamePasswordAuthenticationToken authToken = authenticateToken(token);

        if (authToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return (path.startsWith("/api/auth/") && !path.equals("/api/auth/me")) ||
                path.equals("/") ||
                path.equals("/swagger-ui.html") ||
                path.startsWith("/swagger-ui/") ||
                path.equals("/ws") ||
                path.startsWith("/ws/") ||
                path.equals("/v3/api-docs") ||
                path.startsWith("/v3/api-docs/") ||
                path.startsWith("/api/routing/");
    }
}
