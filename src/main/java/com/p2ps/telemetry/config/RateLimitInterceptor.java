package com.p2ps.telemetry.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.p2ps.telemetry.services.RateLimitingService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    @Value("${telemetry.api.key}")
    private String validApiKey;

    @PostConstruct
    private void validateApiKey() {
        if (validApiKey == null || validApiKey.isBlank()) {
            throw new IllegalStateException("Missing required environment variable TELEMETRY_API_KEY");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // verification of the API
        String requestApiKey = request.getHeader("X-API-Key");
        if (requestApiKey == null || !requestApiKey.equals(validApiKey)) {
            log.warn("[AUTH] Unauthorized telemetry access attempt!");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Invalid or missing API Key");
            return false;
        }

        // verification of the device
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.getWriter().write("Missing X-Device-Id header");
            return false;
        }

        Bucket tokenBucket = rateLimitingService.resolveBucket(deviceId);

        // consuming the token
        if (tokenBucket.tryConsume(1)) {
            return true;
        } else {
            log.warn("[RATE LIMIT] Device {} exceeded 10 req/sec limit!", deviceId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate Limit Exceeded");
            return false;
        }
    }
}
