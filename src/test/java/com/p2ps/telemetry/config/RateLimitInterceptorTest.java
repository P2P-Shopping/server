package com.p2ps.telemetry.config;

import com.p2ps.telemetry.services.RateLimitingService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private HttpServletRequest request;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(rateLimitingService);
        ReflectionTestUtils.setField(interceptor, "validApiKey", "test-telemetry-api-key");
    }

    @Test
    void shouldAllowRequestWithValidApiKeyAndDeviceId() throws Exception {
        Bucket bucket = Bucket.builder().addLimit(io.github.bucket4j.Bandwidth.simple(1, java.time.Duration.ofSeconds(1))).build();
        when(request.getHeader("X-API-Key")).thenReturn("test-telemetry-api-key");
        when(request.getHeader("X-Device-Id")).thenReturn("device-1");
        when(rateLimitingService.resolveBucket("device-1")).thenReturn(bucket);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void shouldRejectRequestWithMissingApiKey() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertEquals("Invalid or missing API Key", response.getContentAsString());
    }

    @Test
    void shouldRejectRequestWithInvalidApiKey() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn("wrong-key");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertEquals("Invalid or missing API Key", response.getContentAsString());
    }

    @Test
    void shouldRejectRequestWithMissingDeviceId() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn("test-telemetry-api-key");
        when(request.getHeader("X-Device-Id")).thenReturn("   ");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(400, response.getStatus());
        assertEquals("Missing X-Device-Id header", response.getContentAsString());
    }

    @Test
    void shouldRejectRequestWithNullDeviceId() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn("test-telemetry-api-key");
        when(request.getHeader("X-Device-Id")).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(400, response.getStatus());
        assertEquals("Missing X-Device-Id header", response.getContentAsString());
    }

    @Test
    void shouldRejectRequestWhenRateLimitIsExceeded() throws Exception {
        Bucket bucket = org.mockito.Mockito.mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(false);

        when(request.getHeader("X-API-Key")).thenReturn("test-telemetry-api-key");
        when(request.getHeader("X-Device-Id")).thenReturn("device-1");
        when(rateLimitingService.resolveBucket("device-1")).thenReturn(bucket);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(429, response.getStatus());
        assertEquals("Rate Limit Exceeded", response.getContentAsString());
    }

    @Test
    void shouldAllowOptionsRequestsWithoutApiKey() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void validateApiKey_missing_throws() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(rateLimitingService);
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(interceptor, "validateApiKey"));
    }

    @Test
    void validateApiKey_present_noThrow() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(rateLimitingService);
        ReflectionTestUtils.setField(interceptor, "validApiKey", "present");
        ReflectionTestUtils.invokeMethod(interceptor, "validateApiKey");
    }
}
