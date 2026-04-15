package com.p2ps.auth.security;


import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class ExtractBearerToken {

        @Test
        void nullInput_ReturnsNull() {
            assertNull(jwtAuthFilter.extractBearerToken(null));
        }

        @Test
        void blankInput_ReturnsNull() {
            assertNull(jwtAuthFilter.extractBearerToken(""));
            assertNull(jwtAuthFilter.extractBearerToken("   "));
        }

        @Test
        void bearerPrefix_ReturnsToken() {
            assertEquals("xyz", jwtAuthFilter.extractBearerToken("Bearer xyz"));
        }

        @Test
        void lowercaseBearer_ReturnsToken() {
            assertEquals("xyz", jwtAuthFilter.extractBearerToken("bearer xyz"));
        }

        @Test
        void uppercaseBearer_ReturnsToken() {
            assertEquals("xyz", jwtAuthFilter.extractBearerToken("BEARER xyz"));
        }

        @Test
        void bareToken_ReturnsToken() {
            assertEquals("xyz", jwtAuthFilter.extractBearerToken("xyz"));
        }

        @Test
        void bearerWithOnlyWhitespace_ReturnsBearer() {
            assertEquals("Bearer", jwtAuthFilter.extractBearerToken("Bearer "));
        }

        @Test
        void bearerWithExtraSpaces_Trimmed() {
            assertEquals("xyz", jwtAuthFilter.extractBearerToken("  Bearer xyz  "));
        }
    }

    @Nested
    class AuthenticateToken {

        @Test
        void nullInput_ReturnsNull() {
            assertNull(jwtAuthFilter.authenticateToken(null));
        }

        @Test
        void blankInput_ReturnsNull() {
            assertNull(jwtAuthFilter.authenticateToken(""));
            assertNull(jwtAuthFilter.authenticateToken("   "));
        }

        @Test
        void validToken_ReturnsAuthentication() {
            when(jwtUtil.extractEmail("valid-token")).thenReturn("test@test.com");
            when(jwtUtil.isTokenExpired("valid-token")).thenReturn(false);

            UsernamePasswordAuthenticationToken result = jwtAuthFilter.authenticateToken("valid-token");

            assertNotNull(result);
            assertEquals("test@test.com", result.getPrincipal());
        }

        @Test
        void nullEmail_ReturnsNull() {
            when(jwtUtil.extractEmail("token")).thenReturn(null);

            assertNull(jwtAuthFilter.authenticateToken("token"));
            verify(jwtUtil, never()).isTokenExpired(any());
        }

        @Test
        void expiredToken_ReturnsNull() {
            when(jwtUtil.extractEmail("expired-token")).thenReturn("test@test.com");
            when(jwtUtil.isTokenExpired("expired-token")).thenReturn(true);

            assertNull(jwtAuthFilter.authenticateToken("expired-token"));
        }

        @Test
        void exceptionDuringExtraction_ReturnsNull() {
            when(jwtUtil.extractEmail("bad-token")).thenThrow(new RuntimeException("parse error"));

            assertNull(jwtAuthFilter.authenticateToken("bad-token"));
        }
    }

    @Nested
    class DoFilterInternal {

        @Test
        void withValidTokenInHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            request.addHeader("Authorization", "Bearer valid-jwt");

            when(jwtUtil.extractEmail("valid-jwt")).thenReturn("test@test.com");
            when(jwtUtil.isTokenExpired("valid-jwt")).thenReturn(false);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("test@test.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void noHeader_ShouldContinueChain() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void expiredToken_ContextNotSet() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            request.addHeader("Authorization", "Bearer expired-jwt");

            when(jwtUtil.extractEmail("expired-jwt")).thenReturn("test@test.com");
            when(jwtUtil.isTokenExpired("expired-jwt")).thenReturn(true);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void alreadyAuthenticated_ContextNotOverwritten() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            UsernamePasswordAuthenticationToken existing = new UsernamePasswordAuthenticationToken("existing", null);
            SecurityContextHolder.getContext().setAuthentication(existing);

            request.addHeader("Authorization", "Bearer new-jwt");
            when(jwtUtil.extractEmail("new-jwt")).thenReturn("new@test.com");
            when(jwtUtil.isTokenExpired("new-jwt")).thenReturn(false);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertEquals("existing", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void exceptionDuringAuth_ContextClearedAndContinues() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            request.addHeader("Authorization", "Bearer bad-jwt");
            when(jwtUtil.extractEmail("bad-jwt")).thenThrow(new RuntimeException("error"));

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void bareTokenWithoutBearerPrefix() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            request.addHeader("Authorization", "raw-token-value");

            when(jwtUtil.extractEmail("raw-token-value")).thenReturn("test@test.com");
            when(jwtUtil.isTokenExpired("raw-token-value")).thenReturn(false);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("test@test.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        }

        @Test
        void cookieTokenTakesPrecedenceOverHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            request.setCookies(new Cookie("jwt-token", "cookie-token"));
            request.addHeader("Authorization", "Bearer header-token");

            when(jwtUtil.extractEmail("cookie-token")).thenReturn("cookie@test.com");
            when(jwtUtil.isTokenExpired("cookie-token")).thenReturn(false);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("cookie@test.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            verify(jwtUtil, never()).extractEmail("header-token");
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    class ShouldNotFilter {

        @Test
        void knownWhitelistedPaths_AreSkipped() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            request.setRequestURI("/api/auth/login");
            assertTrue(jwtAuthFilter.shouldNotFilter(request));

            request.setRequestURI("/swagger-ui/index.html");
            assertTrue(jwtAuthFilter.shouldNotFilter(request));

            request.setRequestURI("/v3/api-docs/openapi");
            assertTrue(jwtAuthFilter.shouldNotFilter(request));

            request.setRequestURI("/api/routing/lookup");
            assertTrue(jwtAuthFilter.shouldNotFilter(request));
        }

        @Test
        void otherPaths_AreNotSkipped() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            request.setRequestURI("/api/products/list");
            assertFalse(jwtAuthFilter.shouldNotFilter(request));
        }
    }
}
