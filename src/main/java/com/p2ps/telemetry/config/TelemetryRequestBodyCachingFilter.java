package com.p2ps.telemetry.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
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
        filterChain.doFilter(new CachedBodyHttpServletRequest(request), response);
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
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
}
