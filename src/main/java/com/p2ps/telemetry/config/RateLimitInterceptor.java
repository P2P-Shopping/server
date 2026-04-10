package com.p2ps.telemetry.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // verification of the API
        String requestApiKey = request.getHeader("X-API-Key");
        if (requestApiKey == null || !requestApiKey.equals(validApiKey)) {
            log.warn("[AUTH] Unauthorized telemetry access attempt!");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or missing API Key");
            return false;
        }

        // verification of the device
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank()) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Missing X-Device-Id header");
            return false;
        }

        Bucket tokenBucket = rateLimitingService.resolveBucket(deviceId);

        // consuming the token
        if (tokenBucket.tryConsume(1)) {
            return true;
        } else {
            log.warn("[RATE LIMIT] Device {} exceeded 10 req/sec limit!", deviceId);
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate Limit Exceeded");
            return false;
        }
    }
}