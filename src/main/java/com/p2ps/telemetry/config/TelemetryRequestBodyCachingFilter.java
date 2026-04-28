package com.p2ps.telemetry.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

@Component
public class TelemetryRequestBodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/telemetry/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            filterChain.doFilter(cached, response);
        } catch (PayloadTooLargeException _) {
            response.sendError(HttpStatus.REQUEST_ENTITY_TOO_LARGE.value(), "Payload too large");
        }
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private static final long MAX_BODY_BYTES = 1 * 1024 * 1024L; // 1 MB cap

        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            long contentLength = request.getContentLengthLong();
            if (contentLength > MAX_BODY_BYTES) {
                throw new PayloadTooLargeException("Declared content-length exceeds limit: " + contentLength);
            }

            super(request);

            if (contentLength > 0) {
                byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
                if (body.length > MAX_BODY_BYTES) {
                    throw new PayloadTooLargeException("Request body exceeds maximum allowed size");
                }
                this.cachedBody = body;
            } else {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                ServletInputStream in = request.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BODY_BYTES) {
                        throw new PayloadTooLargeException("Request body exceeds maximum allowed size");
                    }
                    baos.write(buffer, 0, read);
                }
                this.cachedBody = baos.toByteArray();
            }
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = Charset.forName(getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8");
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final java.io.ByteArrayInputStream inputStream;

        private CachedBodyServletInputStream(byte[] cachedBody) {
            this.inputStream = new java.io.ByteArrayInputStream(cachedBody);
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async IO is not supported");
        }
    }

    private static final class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException(String message) {
            super(message);
        }
    }
}
