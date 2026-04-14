package com.p2ps.telemetry.config;

import com.p2ps.telemetry.services.RateLimitingService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void shouldAllowOptionsRequestsWithoutApiKey() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void shouldAllowTelemetryPingWhenBodyAndHeaderMatch() throws Exception {
        MockHttpServletRequest telemetryRequest = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping");
        telemetryRequest.setContentType("application/json");
        telemetryRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        telemetryRequest.setContent(("{" +
                "\"deviceId\":\"device-1\"," +
                "\"storeId\":\"store-7\"," +
                "\"itemId\":\"item-101\"," +
                "\"lat\":47.151726," +
                "\"lng\":27.587914," +
                "\"accuracyMeters\":3.5," +
                "\"timestamp\":1711888658000}" ).getBytes(StandardCharsets.UTF_8));
        telemetryRequest.addHeader("X-API-Key", "test-telemetry-api-key");
        telemetryRequest.addHeader("X-Device-Id", "device-1");

        Bucket bucket = Bucket.builder().addLimit(io.github.bucket4j.Bandwidth.simple(10, Duration.ofSeconds(1))).build();
        when(rateLimitingService.resolveBucket("device-1")).thenReturn(bucket);

        assertTrue(interceptor.preHandle(telemetryRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldRejectTelemetryPingWhenHeaderAndBodyDeviceMismatch() throws Exception {
        MockHttpServletRequest telemetryRequest = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping");
        telemetryRequest.setContentType("application/json");
        telemetryRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        telemetryRequest.setContent(("{" +
                "\"deviceId\":\"device-1\"," +
                "\"storeId\":\"store-7\"," +
                "\"itemId\":\"item-101\"," +
                "\"lat\":47.151726," +
                "\"lng\":27.587914," +
                "\"accuracyMeters\":3.5," +
                "\"timestamp\":1711888658000}" ).getBytes(StandardCharsets.UTF_8));
        telemetryRequest.addHeader("X-API-Key", "test-telemetry-api-key");
        telemetryRequest.addHeader("X-Device-Id", "device-2");

        assertFalse(interceptor.preHandle(telemetryRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldRejectEmptyBatchPayload() throws Exception {
        MockHttpServletRequest telemetryRequest = new MockHttpServletRequest("POST", "/api/v1/telemetry/batch");
        telemetryRequest.setContentType("application/json");
        telemetryRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        telemetryRequest.setContent("{\"pings\":[]}".getBytes(StandardCharsets.UTF_8));
        telemetryRequest.addHeader("X-API-Key", "test-telemetry-api-key");

        assertFalse(interceptor.preHandle(telemetryRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldRejectOversizedBatchPayload() throws Exception {
        StringBuilder json = new StringBuilder("{\"pings\":[");
        for (int i = 0; i < 1001; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"deviceId\":\"device-1\",\"storeId\":\"store-7\",\"itemId\":\"item-")
                    .append(i)
                    .append("\",\"lat\":47.151726,\"lng\":27.587914,\"accuracyMeters\":3.5,\"timestamp\":1711888658000}");
        }
        json.append("]}");

        MockHttpServletRequest telemetryRequest = new MockHttpServletRequest("POST", "/api/v1/telemetry/batch");
        telemetryRequest.setContentType("application/json");
        telemetryRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        telemetryRequest.setContent(json.toString().getBytes(StandardCharsets.UTF_8));
        telemetryRequest.addHeader("X-API-Key", "test-telemetry-api-key");

        assertFalse(interceptor.preHandle(telemetryRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldRejectBatchWithMultipleDeviceIds() throws Exception {
        MockHttpServletRequest telemetryRequest = new MockHttpServletRequest("POST", "/api/v1/telemetry/batch");
        telemetryRequest.setContentType("application/json");
        telemetryRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        telemetryRequest.setContent(("{" +
                "\"pings\":[{" +
                "\"deviceId\":\"device-1\",\"storeId\":\"store-7\",\"itemId\":\"item-1\",\"lat\":47.151726,\"lng\":27.587914,\"accuracyMeters\":3.5,\"timestamp\":1711888658000" +
                "},{" +
                "\"deviceId\":\"device-2\",\"storeId\":\"store-7\",\"itemId\":\"item-2\",\"lat\":47.151726,\"lng\":27.587914,\"accuracyMeters\":3.5,\"timestamp\":1711888658000" +
                "}]}" ).getBytes(StandardCharsets.UTF_8));
        telemetryRequest.addHeader("X-API-Key", "test-telemetry-api-key");

        assertFalse(interceptor.preHandle(telemetryRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldRejectTelemetryWhenHeaderBodyMismatchInBatch() throws Exception {
        MockHttpServletRequest telemetryRequest = new MockHttpServletRequest("POST", "/api/v1/telemetry/batch");
        telemetryRequest.setContentType("application/json");
        telemetryRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        telemetryRequest.setContent(("{" +
                "\"pings\":[{" +
                "\"deviceId\":\"device-1\",\"storeId\":\"store-7\",\"itemId\":\"item-1\",\"lat\":47.151726,\"lng\":27.587914,\"accuracyMeters\":3.5,\"timestamp\":1711888658000" +
                "}]}" ).getBytes(StandardCharsets.UTF_8));
        telemetryRequest.addHeader("X-API-Key", "test-telemetry-api-key");
        telemetryRequest.addHeader("X-Device-Id", "device-2");

        assertFalse(interceptor.preHandle(telemetryRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldRejectMalformedJsonPing() throws Exception {
        MockHttpServletRequest telemetryRequest = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping");
        telemetryRequest.setContentType("application/json");
        telemetryRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        telemetryRequest.setContent("{".getBytes(StandardCharsets.UTF_8));
        telemetryRequest.addHeader("X-API-Key", "test-telemetry-api-key");

        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(telemetryRequest, resp, new Object()));
        assertEquals(400, resp.getStatus());
    }

    @Test
    void shouldRejectBatchWithNullPingEntries() throws Exception {
        String json = "{\"pings\":[null,{\"deviceId\":\"device-1\",\"storeId\":\"store-7\",\"itemId\":\"item-1\",\"lat\":47.151726,\"lng\":27.587914,\"accuracyMeters\":3.5,\"timestamp\":1711888658000}]}";
        MockHttpServletRequest telemetryRequest = new MockHttpServletRequest("POST", "/api/v1/telemetry/batch");
        telemetryRequest.setContentType("application/json");
        telemetryRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        telemetryRequest.setContent(json.getBytes(StandardCharsets.UTF_8));
        telemetryRequest.addHeader("X-API-Key", "test-telemetry-api-key");

        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(telemetryRequest, resp, new Object()));
        assertEquals(400, resp.getStatus());
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
