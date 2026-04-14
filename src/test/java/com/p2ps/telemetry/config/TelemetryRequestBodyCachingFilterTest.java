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
