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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    void shouldRejectOversizedPayload_byDeclaredLength() throws Exception {
        TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();

        MockHttpServletRequest req = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return 2L * 1024L * 1024L; // 2 MB declared
            }
        };
        req.setMethod("POST");
        req.setRequestURI("/api/v1/telemetry/ping");

        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (request, response) -> {});

        assertEquals(413, resp.getStatus());
    }

    @Test
    void shouldRejectOversizedPayload_byStreamRead() throws Exception {
        TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/telemetry/ping");

        // supply a large body while leaving content-length as zero
        byte[] big = new byte[2 * 1024 * 1024];
        for (int i = 0; i < big.length; i++) {
            big[i] = 'x';
        }
        req.setContent(big);
        req.addHeader("Content-Length", "0");

        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (request, response) -> {});

        assertEquals(413, resp.getStatus());
    }

    @Test
    void shouldRejectOversizedPayloadWhenContentLengthUnknown() throws Exception {
        TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();
        int size = 1 * 1024 * 1024 + 10; // slightly over 1MB cap used by the filter
        byte[] large = new byte[size];
        for (int i = 0; i < large.length; i++) {
            large[i] = 'a';
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping") {
            @Override
            public long getContentLengthLong() {
                return -1L; // unknown length (chunked)
            }

            @Override
            public jakarta.servlet.ServletInputStream getInputStream() {
                final java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(large);
                return new jakarta.servlet.ServletInputStream() {
                    @Override
                    public int read() throws IOException {
                        return in.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return in.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(jakarta.servlet.ReadListener readListener) {
                        // no-op for tests
                    }
                };
            }
        };
        req.setContentType("application/json");

        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> fail("FilterChain should not be invoked for oversized payload");

        filter.doFilter(req, resp, chain);

        assertEquals(413, resp.getStatus());
    }

    @Test
    void getReader_returnsContent_and_inputStream_behaviour() throws Exception {
        TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();
        String payload = "こんにちは"; // multi-byte characters
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping");
        req.setContentType("application/json");
        req.setCharacterEncoding(StandardCharsets.UTF_8.name());
        req.setContent(payload.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicReference<jakarta.servlet.ServletRequest> captured = new AtomicReference<>();
        FilterChain chain = (request, response) -> captured.set(request);

        filter.doFilter(req, resp, chain);

        jakarta.servlet.http.HttpServletRequest wrapped = (jakarta.servlet.http.HttpServletRequest) captured.get();

        // reader should return the original string
        String read = wrapped.getReader().lines().collect(Collectors.joining("\n"));
        assertEquals(payload, read);

        // input stream should be repeatable
        byte[] first = wrapped.getInputStream().readAllBytes();
        byte[] second = wrapped.getInputStream().readAllBytes();
        assertArrayEquals(first, second);
    }

    @Test
    void cachedServletInputStream_setReadListener_throwsUnsupported() throws Exception {
        TelemetryRequestBodyCachingFilter filter = new TelemetryRequestBodyCachingFilter();
        String payload = "ok";
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/ping");
        req.setContent(payload.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicReference<jakarta.servlet.ServletRequest> captured = new AtomicReference<>();
        FilterChain chain = (request, response) -> captured.set(request);

        filter.doFilter(req, resp, chain);

        jakarta.servlet.http.HttpServletRequest wrapped = (jakarta.servlet.http.HttpServletRequest) captured.get();
        jakarta.servlet.ServletInputStream in = wrapped.getInputStream();

        assertThrows(UnsupportedOperationException.class, () -> in.setReadListener(null));
    }
}
