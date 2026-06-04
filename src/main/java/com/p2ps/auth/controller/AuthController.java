package com.p2ps.auth.controller;

import com.p2ps.auth.security.dto.LoginRequest;
import com.p2ps.auth.dto.RegisterRequest;
import com.p2ps.auth.security.JwtUtil;
import com.p2ps.auth.service.UserService;
import com.p2ps.auth.model.Users;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_ERROR = "error";

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;

    @Value("${app.security.cookie-secure-flag:true}")
    private boolean isCookieSecure;

    public AuthController(UserService userService, AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil, RestTemplate restTemplate) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "X-Return-Token", required = false) String returnToken) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = auth.getName();
        return userService.findByEmail(email)
                .map(user -> {
                    Map<String, Object> response = toUserResponse(user);
                    if ("true".equalsIgnoreCase(returnToken)) {
                        String token = jwtUtil.generateToken(email, user.getTokenVersion());
                        response.put("token", token);
                    }
                    return ResponseEntity.ok(response);
                })
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
        return ResponseEntity.ok(Map.of(KEY_MESSAGE, "User registered successfully!"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Return-Token", required = false) String returnToken,
            HttpServletRequest servletRequest) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String principalName = auth.getName();

        return userService.findByEmail(principalName)
                .map(user -> {
                    String token = jwtUtil.generateToken(principalName, user.getTokenVersion());
                    ResponseCookie cookie = createJwtCookie(token, 24L * 60 * 60, servletRequest.isSecure());
                    Map<String, Object> data = toUserResponse(user);

                    if ("true".equalsIgnoreCase(returnToken)) {
                        data.put("token", token);
                    }
                    data.put(KEY_MESSAGE, "Login successful");
                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(data);
                })
                .orElseGet(() -> {
                    logger.error("User authenticated successfully but record is missing in database.");
                    Map<String, Object> errorBody = new HashMap<>();
                    errorBody.put(KEY_ERROR, "User record missing after authentication");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorBody);
                });
    }

    private Map<String, Object> toUserResponse(Users user) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", user.getEmail());
        data.put("firstName", user.getFirstName());
        data.put("lastName", user.getLastName());
        data.put("userId", user.getId().toString());
        data.put("hasProfilePicture", user.getProfilePicture() != null);
        return data;
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = auth.getName();
        String firstName = body.get("firstName");
        String lastName = body.get("lastName");
        try {
            Users updated = userService.updateProfile(email, firstName, lastName);
            return ResponseEntity.ok(toUserResponse(updated));
        } catch (Exception e) {
            logger.error("Failed to update profile", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "Failed to update profile"));
        }
    }

    @PostMapping(value = "/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadProfilePicture(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "File is empty"));
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of(KEY_ERROR, "Only JPEG and PNG are allowed"));
        }
        try {
            Users updated = userService.updateProfilePicture(auth.getName(), file.getBytes(), contentType);
            return ResponseEntity.ok(Map.of(
                    KEY_MESSAGE, "Profile picture updated successfully",
                    "hasProfilePicture", true
            ));
        } catch (IOException e) {
            logger.error("Failed to read profile picture", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "Failed to upload profile picture"));
        }
    }

    @GetMapping("/profile-picture")
    public ResponseEntity<byte[]> getProfilePicture() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userService.findByEmail(auth.getName())
                .filter(u -> u.getProfilePicture() != null)
                .map(u -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(u.getProfilePictureContentType()))
                        .body(u.getProfilePicture()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/profile-picture/{userId}")
    public ResponseEntity<byte[]> getProfilePictureByUserId(@PathVariable Integer userId) {
        return userService.findById(userId)
                .filter(u -> u.getProfilePicture() != null)
                .map(u -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(u.getProfilePictureContentType()))
                        .body(u.getProfilePicture()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleLogin(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Return-Token", required = false) String returnToken,
            HttpServletRequest servletRequest) {

        String credential = body.get("credential");
        if (credential == null || credential.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Missing credential"));
        }

        try {
            String verifyUrl = "https://www.googleapis.com/oauth2/v1/userinfo?access_token=" + credential;
            @SuppressWarnings("unchecked")
            Map<String, String> googleData = restTemplate.getForObject(verifyUrl, Map.class);

            if (googleData == null || googleData.get("email") == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, "Invalid Google token"));
            }

            String email = googleData.get("email");
            String googleId = googleData.get("id");
            String firstName = googleData.getOrDefault("given_name", "User");
            String lastName = googleData.getOrDefault("family_name", "");

            Users user = userService.findOrCreateGoogleUser(googleId, email, firstName, lastName);
            String token = jwtUtil.generateToken(email, user.getTokenVersion());
            ResponseCookie cookie = createJwtCookie(token, 24L * 60 * 60, servletRequest.isSecure());

            Map<String, Object> data = toUserResponse(user);
            if ("true".equalsIgnoreCase(returnToken)) {
                data.put("token", token);
            }
            data.put(KEY_MESSAGE, "Login successful");

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(data);
        } catch (Exception e) {
            logger.error("Google login failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_ERROR, "Google authentication failed"));
        }
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
