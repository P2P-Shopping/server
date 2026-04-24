package com.p2ps.auth.controller;

import com.p2ps.auth.security.dto.LoginRequest;
import com.p2ps.auth.dto.RegisterRequest;
import com.p2ps.auth.security.JwtUtil;
import com.p2ps.auth.service.UserService;
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
import java.util.Optional;

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
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = auth.getName();
        return userService.findByEmail(email)
                .map(user -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("email", user.getEmail());
                    data.put("firstName", user.getFirstName());
                    data.put("userId", user.getId().toString());
                    return ResponseEntity.ok(data);
                })
                .orElseGet(() -> {
                    logger.warn("Authenticated user {} not found in database.", email);
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
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String email = request.getEmail();
        String token = jwtUtil.generateToken(email);

        ResponseCookie cookie = createJwtCookie(token, 24L * 60 * 60, servletRequest.isSecure());

        return userService.findByEmail(email)
                .map(user -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("message", "Login successful");
                    data.put("email", user.getEmail());
                    data.put("firstName", user.getFirstName());
                    data.put("userId", user.getId().toString());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(data);
                })
                .orElseGet(() -> {
                    logger.error("User {} authenticated successfully but record is missing in database.", email);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "User record missing after authentication"));
                });
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
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
