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
import org.mockito.ArgumentCaptor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityConfigTest {

    private final JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
    private final CorsConfigurationSource corsSource = mock(CorsConfigurationSource.class);
    private final SecurityConfig config = new SecurityConfig(jwtAuthFilter, corsSource);

    @Test
    void passwordEncoder_ReturnsBCryptPasswordEncoder() {
        PasswordEncoder encoder = config.passwordEncoder();

        assertNotNull(encoder);
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
    }

    @Test
    void authenticationManager_ReturnsProviderManagerWithDaoProvider() {
        PasswordEncoder passwordEncoder = config.passwordEncoder();
        UserService userService = mock(UserService.class);

        AuthenticationManager manager = config.authenticationManager(mock(HttpSecurity.class), passwordEncoder, userService);

        assertNotNull(manager);
        assertInstanceOf(ProviderManager.class, manager);

        ProviderManager providerManager = (ProviderManager) manager;
        assertEquals(1, providerManager.getProviders().size());
        assertInstanceOf(DaoAuthenticationProvider.class, providerManager.getProviders().get(0));
    }

    @Test
    void securityFilterChain_DisablesCsrfAndKeepsStatelessSecurity() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        when(http.csrf(any())).thenReturn(http);
        when(http.cors(any())).thenReturn(http);
        when(http.exceptionHandling(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.sessionManagement(any())).thenReturn(http);
        when(http.addFilterBefore(any(), eq(UsernamePasswordAuthenticationFilter.class))).thenReturn(http);
        DefaultSecurityFilterChain filterChain = mock(DefaultSecurityFilterChain.class);
        when(http.build()).thenReturn(filterChain);

        SecurityFilterChain chain = config.securityFilterChain(http);

        assertSame(filterChain, chain);
        verify(http).csrf(any());
        verify(http).cors(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Customizer> sessionCaptor = ArgumentCaptor.forClass(Customizer.class);
        verify(http).sessionManagement(sessionCaptor.capture());
        @SuppressWarnings("unchecked")
        Customizer<SessionManagementConfigurer<HttpSecurity>> sessionCustomizer = (Customizer<SessionManagementConfigurer<HttpSecurity>>) sessionCaptor.getValue();
        SessionManagementConfigurer<HttpSecurity> sessionConfigurer = mock(SessionManagementConfigurer.class);
        sessionCustomizer.customize(sessionConfigurer);
        verify(sessionConfigurer).sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        verify(http).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
