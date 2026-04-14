package com.p2ps.telemetry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.p2ps.telemetry.dto.TelemetryBatchDTO;
import com.p2ps.telemetry.dto.TelemetryPingDTO;
import com.p2ps.telemetry.services.RateLimitingService;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_BATCH_SIZE = 1000;

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telemetry.api.key}")
    private String validApiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // verification of the API
        String requestApiKey = request.getHeader("X-API-Key");
        if (requestApiKey == null || !requestApiKey.equals(validApiKey)) {
            log.warn("[AUTH] Unauthorized telemetry access attempt!");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or missing API Key");
            return false;
        }

        String requestBody = readRequestBody(request);

        String requestUri = request.getRequestURI();
        if (requestBody.isBlank() || requestUri == null) {
            String deviceId = request.getHeader("X-Device-Id");
            if (deviceId == null || deviceId.isBlank()) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Missing X-Device-Id header");
                return false;
            }

            Bucket tokenBucket = rateLimitingService.resolveBucket(deviceId);
            if (tokenBucket.tryConsume(1)) {
                return true;
            }

            log.warn("[RATE LIMIT] Device {} exceeded 10 req/sec limit!", deviceId);
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate Limit Exceeded");
            return false;
        }

        String deviceId;
        int batchSize;

        if (requestUri.endsWith("/batch")) {
            TelemetryBatchDTO batchDTO = objectMapper.readValue(requestBody, TelemetryBatchDTO.class);
            if (batchDTO.getPings() == null || batchDTO.getPings().isEmpty()) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Batch must contain at least one ping");
                return false;
            }

            batchSize = batchDTO.getPings().size();
            if (batchSize > MAX_BATCH_SIZE) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Batch exceeds maximum size of " + MAX_BATCH_SIZE);
                return false;
            }

            Set<String> deviceIds = batchDTO.getPings().stream()
                    .map(TelemetryPingDTO::getDeviceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (deviceIds.size() != 1) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Batch must contain pings for a single deviceId");
                return false;
            }

            deviceId = deviceIds.iterator().next();
        } else {
            TelemetryPingDTO pingDTO = objectMapper.readValue(requestBody, TelemetryPingDTO.class);
            deviceId = pingDTO.getDeviceId();
            batchSize = 1;
        }

        if (deviceId == null || deviceId.isBlank()) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Missing deviceId in request body");
            return false;
        }

        String headerDeviceId = request.getHeader("X-Device-Id");
        if (headerDeviceId != null && !headerDeviceId.isBlank() && !headerDeviceId.equals(deviceId)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "X-Device-Id header does not match request body");
            return false;
        }

        Bucket tokenBucket = rateLimitingService.resolveBucket(deviceId);

        if (tokenBucket.tryConsume(batchSize)) {
            return true;
        } else {
            log.warn("[RATE LIMIT] Device {} exceeded 10 req/sec limit for {} pings!", deviceId, batchSize);
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate Limit Exceeded");
            return false;
        }
    }

    private String readRequestBody(HttpServletRequest request) {
        try {
            if (request.getInputStream() == null) {
                return "";
            }

            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }
}
