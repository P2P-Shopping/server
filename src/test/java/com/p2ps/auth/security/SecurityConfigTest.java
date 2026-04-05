package com.p2ps.auth.security;

import com.p2ps.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityConfigTest {

    @Test
    void passwordEncoder_ReturnsBCryptPasswordEncoder() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        SecurityConfig config = new SecurityConfig(jwtAuthFilter);

        PasswordEncoder encoder = config.passwordEncoder();

        assertNotNull(encoder);
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
    }

    @Test
    void authenticationManager_ReturnsProviderManagerWithDaoProvider() throws Exception {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        SecurityConfig config = new SecurityConfig(jwtAuthFilter);
        PasswordEncoder passwordEncoder = config.passwordEncoder();
        UserService userService = mock(UserService.class);

        AuthenticationManager manager = config.authenticationManager(mock(org.springframework.security.config.annotation.web.builders.HttpSecurity.class), passwordEncoder, userService);

        assertNotNull(manager);
        assertInstanceOf(ProviderManager.class, manager);

        ProviderManager providerManager = (ProviderManager) manager;
        assertEquals(1, providerManager.getProviders().size());
        assertInstanceOf(DaoAuthenticationProvider.class, providerManager.getProviders().get(0));
    }

    @Test
    void corsConfigurationSource_ConfiguredCorrectly() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        SecurityConfig config = new SecurityConfig(jwtAuthFilter);

        CorsConfigurationSource source = config.corsConfigurationSource();

        assertNotNull(source);
        assertInstanceOf(UrlBasedCorsConfigurationSource.class, source);

        CorsConfiguration corsConfig = ((UrlBasedCorsConfigurationSource) source).getCorsConfiguration(new MockHttpServletRequest("/api/test"));

        assertNotNull(corsConfig);
        assertEquals(List.of("http://localhost:5173"), corsConfig.getAllowedOrigins());
        assertEquals(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"), corsConfig.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type", "Accept"), corsConfig.getAllowedHeaders());
        assertTrue(corsConfig.getAllowCredentials());
    }

    private static class MockHttpServletRequest extends org.springframework.mock.web.MockHttpServletRequest {
        public MockHttpServletRequest(String pathInfo) {
            setRequestURI(pathInfo);
            setServletPath(pathInfo);
        }
    }
}
