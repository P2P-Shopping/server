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
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Constructor;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = newRateLimitInterceptor(rateLimitingService);
        ReflectionTestUtils.setField(interceptor, "validApiKey", "test-telemetry-api-key");
    }

    @Test
    void shouldAllowRequestWithValidApiKeyAndDeviceId() throws Exception {
        Bucket bucket = Bucket.builder().addLimit(io.github.bucket4j.Bandwidth.simple(1, java.time.Duration.ofSeconds(1))).build();
        when(request.getHeader("X-API-Key")).thenReturn("test-telemetry-api-key");
        when(request.getHeader("X-Device-Id")).thenReturn("device-1");
        when(rateLimitingService.resolveBucket("device-1")).thenReturn(bucket);

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void shouldRejectRequestWithMissingApiKey() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn(null);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).sendError(401, "Invalid or missing API Key");
    }

    @Test
    void shouldRejectRequestWithInvalidApiKey() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn("wrong-key");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).sendError(401, "Invalid or missing API Key");
    }

    @Test
    void shouldRejectRequestWithMissingDeviceId() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn("test-telemetry-api-key");
        when(request.getHeader("X-Device-Id")).thenReturn("   ");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).sendError(400, "Missing X-Device-Id header");
    }

    @Test
    void shouldRejectRequestWithNullDeviceId() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn("test-telemetry-api-key");
        when(request.getHeader("X-Device-Id")).thenReturn(null);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).sendError(400, "Missing X-Device-Id header");
    }

    @Test
    void shouldRejectRequestWhenRateLimitIsExceeded() throws Exception {
        Bucket bucket = Bucket.builder().addLimit(io.github.bucket4j.Bandwidth.simple(1, java.time.Duration.ofSeconds(1))).build();
        when(request.getHeader("X-API-Key")).thenReturn("test-telemetry-api-key");
        when(request.getHeader("X-Device-Id")).thenReturn("device-1");
        when(rateLimitingService.resolveBucket("device-1")).thenReturn(bucket);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).sendError(429, "Rate Limit Exceeded");
    }

    private RateLimitInterceptor newRateLimitInterceptor(RateLimitingService service) {
        try {
            Constructor<RateLimitInterceptor> constructor = RateLimitInterceptor.class.getDeclaredConstructor(RateLimitingService.class);
            constructor.setAccessible(true);
            return constructor.newInstance(service);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create RateLimitInterceptor", e);
        }
    }
}
