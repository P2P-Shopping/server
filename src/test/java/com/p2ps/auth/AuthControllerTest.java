package com.p2ps.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.auth.dto.RegisterRequest;
import com.p2ps.auth.model.Users;
import com.p2ps.auth.security.JwtUtil;
import com.p2ps.auth.security.dto.LoginRequest;
import com.p2ps.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void register_ShouldReturnCreated() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Password123!");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(userService.registerUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Users());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void register_ShouldReturnBadRequest_WhenValidationFails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("email_invalid");
        request.setPassword("");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_ShouldReturnOk() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("Password123!");

        Authentication auth = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());

        Users mockUser = new Users();
        mockUser.setEmail(request.getEmail());
        mockUser.setFirstName("John");
        mockUser.setId(1);

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);
        when(jwtUtil.generateToken(anyString(), anyInt())).thenReturn("mocked-jwt-token-123");
        when(userService.findByEmail(request.getEmail()))
                .thenReturn(java.util.Optional.of(mockUser));

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Return-Token", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("jwt-token=mocked-jwt-token-123")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(jsonPath("$.token").value("mocked-jwt-token-123"))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.userId").value("1"));
    }

    @Test
    void login_ShouldOmitToken_WhenHeaderIsMissing() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("Password123!");

        Authentication auth = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());
        Users mockUser = new Users();
        mockUser.setEmail(request.getEmail());
        mockUser.setFirstName("John");
        mockUser.setId(1);

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);
        when(jwtUtil.generateToken(anyString(), anyInt())).thenReturn("mocked-jwt-token-123");
        when(userService.findByEmail(request.getEmail()))
                .thenReturn(java.util.Optional.of(mockUser));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void login_ShouldReturnUnauthorized_WhenCredentialsAreInvalid() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("wrong@example.com");
        request.setPassword("wrongpass");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication
                        .BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_ShouldReturnConflict_WhenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existent@example.com");
        request.setPassword("Password123!");
        request.setFirstName("Ion");
        request.setLastName("Popescu");

        when(userService.registerUser(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new com.p2ps.exception.UserAlreadyExistsException("Email already in use!"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_ShouldReturnBadRequest_WhenNamesAreBlank() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("valid@email.com");
        request.setPassword("Password123!");
        request.setFirstName("");
        request.setLastName("");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "test@example.com")
    void me_ShouldReturnUserData() throws Exception {
        Users mockUser = new Users();
        mockUser.setEmail("test@example.com");
        mockUser.setFirstName("John");
        mockUser.setId(1);

        when(userService.findByEmail("test@example.com"))
                .thenReturn(java.util.Optional.of(mockUser));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    void me_ShouldReturnUnauthorized_WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_ShouldReturnOkAndClearCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("jwt-token=")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void login_ShouldReturnInternalServerError_WhenUserNotFoundInDbAfterAuth() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("Password123!");

        Authentication auth = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);
        when(jwtUtil.generateToken(anyString(), anyInt())).thenReturn("mocked-jwt-token-123");
        when(userService.findByEmail(request.getEmail())).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("User record missing after authentication"));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "missing@example.com")
    void me_ShouldReturnUnauthorized_WhenUserRecordMissingInDb() throws Exception {
        when(userService.findByEmail("missing@example.com"))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_ShouldSetSecureCookie_WhenRequestIsSecure() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("Password123!");

        Authentication auth = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());
        Users mockUser = new Users();
        mockUser.setEmail(request.getEmail());
        mockUser.setFirstName("John");
        mockUser.setId(1);

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);
        when(jwtUtil.generateToken(anyString(), anyInt())).thenReturn("mock-token");
        when(userService.findByEmail(request.getEmail()))
                .thenReturn(java.util.Optional.of(mockUser));

        mockMvc.perform(post("/api/auth/login")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
    }

    @Test
    void logout_ShouldSetSecureCookie_WhenRequestIsSecure() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
    }
}