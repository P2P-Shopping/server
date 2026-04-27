package com.p2ps.auth.controller;

import com.p2ps.auth.security.dto.LoginRequest;
import com.p2ps.auth.dto.RegisterRequest;
import com.p2ps.auth.security.JwtUtil;
import com.p2ps.auth.service.UserService;
import com.p2ps.auth.model.Users;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${app.security.cookie-secure-flag:true}")
    private boolean isCookieSecure;

    public AuthController(UserService userService, AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = auth.getName();
        return userService.findByEmail(email)
                .map(user -> ResponseEntity.ok(toUserResponse(user)))
                .orElseGet(() -> {
                    logger.warn("Authenticated user record not found in database.");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                });
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        userService.registerUser(
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName()
        );
        return ResponseEntity.ok(Map.of("message", "User registered successfully!"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String principalName = auth.getName();

        return userService.findByEmail(principalName)
                .map(user -> {
                    String token = jwtUtil.generateToken(principalName, user.getTokenVersion());
                    ResponseCookie cookie = createJwtCookie(token, 24L * 60 * 60, servletRequest.isSecure());
                    Map<String, Object> data = toUserResponse(user);
                    
                    if ("true".equalsIgnoreCase(servletRequest.getHeader("X-Return-Token"))) {
                        data.put("token", token);
                    }
                    
                    data.put("message", "Login successful");
                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(data);
                })
                .orElseGet(() -> {
                    logger.error("User authenticated successfully but record is missing in database.");
                    Map<String, Object> errorBody = new HashMap<>();
                    errorBody.put("error", "User record missing after authentication");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorBody);
                });
    }

    private Map<String, Object> toUserResponse(Users user) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", user.getEmail());
        data.put("firstName", user.getFirstName());
        data.put("userId", user.getId().toString());
        return data;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            userService.incrementTokenVersion(auth.getName());
        }
        ResponseCookie cookie = createJwtCookie("", 0, servletRequest.isSecure());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private ResponseCookie createJwtCookie(String token, long maxAge, boolean requestIsSecure) {
        return ResponseCookie.from("jwt-token", token)
                .httpOnly(true)
                .secure(isCookieSecure || requestIsSecure)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
