package com.p2ps.telemetry.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryRequestBodyCachingFilterTest {

    @Test
    void shouldNotFilterNonTelemetryUri() throws Exception {
        TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");

        assertTrue(filter.shouldNotFilter(req));
    }

    @Test
    void shouldCacheRequestAndAllowMultipleReads() throws Exception {
        TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();
        String payload = "{\"deviceId\":\"device-1\",\"lat\":1.0}";
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping");
        req.setContentType("application/json");
        req.setCharacterEncoding(StandardCharsets.UTF_8.name());
        req.setContent(payload.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse resp = new MockHttpServletResponse();

        final byte[][] reads = new byte[2][];
        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                reads[0] = request.getInputStream().readAllBytes();
                reads[1] = request.getInputStream().readAllBytes();
            }
        };

        filter.doFilter(req, resp, chain);

        assertArrayEquals(payload.getBytes(StandardCharsets.UTF_8), reads[0]);
        assertArrayEquals(reads[0], reads[1]);
    }

    @Test
    void shouldRejectOversizedPayload() throws Exception {
        TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();
        int size = 1 * 1024 * 1024 + 10; // slightly over 1MB cap used by the filter
        byte[] large = new byte[size];
        for (int i = 0; i < large.length; i++) {
            large[i] = 'a';
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping");
        req.setContentType("application/json");
        req.setContent(large);

        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> fail("FilterChain should not be invoked for oversized payload");

        filter.doFilter(req, resp, chain);

        assertEquals(413, resp.getStatus());
    }
}
package com.p2ps.telemetry.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TelemetryRequestBodyCachingFilterTest {

    private final TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();

    @Test
    void shouldFilterTelemetryRequestsAndAllowBodyToBeReadTwice() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("{\"deviceId\":\"device-1\"}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(response));
        assertEquals("{\"deviceId\":\"device-1\"}", request.getContentAsString());
    }

    @Test
    void shouldNotFilterNonTelemetryRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        assertEquals(true, filter.shouldNotFilter(request));
    }

    @Test
    void shouldWrapRequestInputStreamAndReader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/telemetry/batch");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("{\"pings\":[]}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            BufferedReader reader = wrappedRequest.getReader();
            assertNotNull(reader);
            assertEquals("{\"pings\":[]}", reader.readLine());
        };

        filter.doFilter(request, response, chain);
    }
}
