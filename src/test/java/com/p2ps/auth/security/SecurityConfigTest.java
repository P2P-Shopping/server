package com.p2ps.auth.security;

import com.p2ps.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
        assertEquals(List.of("Authorization", "Content-Type", "Accept", "X-Return-Token"), corsConfig.getAllowedHeaders());
        assertTrue(corsConfig.getAllowCredentials());
    }

    @Test
    void corsConfigurationSource_DisallowsWildcardWithCredentials() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        SecurityConfig config = new SecurityConfig(jwtAuthFilter);
        org.springframework.test.util.ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:5173, *");

        CorsConfiguration corsConfig = ((UrlBasedCorsConfigurationSource) config.corsConfigurationSource())
                .getCorsConfiguration(new MockHttpServletRequest("/api/test"));

        assertNotNull(corsConfig);
        // * should be filtered out
        assertEquals(List.of("http://localhost:5173"), corsConfig.getAllowedOrigins());
    }

    @Test
    void corsConfigurationSource_TrimsAndIgnoresEmptyOrigins() {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        SecurityConfig config = new SecurityConfig(jwtAuthFilter);
        org.springframework.test.util.ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:5173, https://example.com, ");

        CorsConfiguration corsConfig = ((UrlBasedCorsConfigurationSource) config.corsConfigurationSource())
                .getCorsConfiguration(new MockHttpServletRequest("/api/test"));

        assertNotNull(corsConfig);
        assertEquals(List.of("http://localhost:5173", "https://example.com"), corsConfig.getAllowedOrigins());
    }

    @Test
    void securityFilterChain_DisablesCsrfAndKeepsStatelessSecurity() throws Exception {
        JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
        SecurityConfig config = new SecurityConfig(jwtAuthFilter);

        HttpSecurity http = mock(HttpSecurity.class);
        when(http.csrf(any())).thenReturn(http);
        when(http.cors(any())).thenReturn(http);
        when(http.exceptionHandling(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.sessionManagement(any())).thenReturn(http);
        when(http.addFilterBefore(any(), eq(org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class))).thenReturn(http);
        DefaultSecurityFilterChain filterChain = mock(DefaultSecurityFilterChain.class);
        when(http.build()).thenReturn(filterChain);

        SecurityFilterChain chain = config.securityFilterChain(http);

        assertSame(filterChain, chain);
        verify(http).csrf(any());

        // verify session management is configured to be STATELESS
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Customizer> sessionCaptor = ArgumentCaptor.forClass(Customizer.class);
        verify(http).sessionManagement(sessionCaptor.capture());
        @SuppressWarnings("unchecked")
        Customizer<SessionManagementConfigurer<HttpSecurity>> sessionCustomizer = (Customizer<SessionManagementConfigurer<HttpSecurity>>) sessionCaptor.getValue();
        SessionManagementConfigurer<HttpSecurity> sessionConfigurer = mock(SessionManagementConfigurer.class);
        sessionCustomizer.customize(sessionConfigurer);
        verify(sessionConfigurer).sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // verify JwtAuthFilter registered before UsernamePasswordAuthenticationFilter
        verify(http).addFilterBefore(eq(jwtAuthFilter), eq(UsernamePasswordAuthenticationFilter.class));
    }

    private static class MockHttpServletRequest extends org.springframework.mock.web.MockHttpServletRequest {
        public MockHttpServletRequest(String pathInfo) {
            setRequestURI(pathInfo);
            setServletPath(pathInfo);
        }
    }
}
