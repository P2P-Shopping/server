package com.p2ps.telemetry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
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
            TelemetryBatchDTO batchDTO;
            try {
                batchDTO = objectMapper.readValue(requestBody, TelemetryBatchDTO.class);
            } catch (IOException ex) {
                log.warn("[RATE LIMIT] Malformed batch JSON", ex);
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Malformed JSON in request body");
                return false;
            } catch (RuntimeException ex) {
                log.warn("[RATE LIMIT] Error parsing batch payload", ex);
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid batch payload");
                return false;
            }

            List<TelemetryPingDTO> pings = batchDTO.getPings();
            if (pings == null || pings.isEmpty()) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Batch must contain at least one ping");
                return false;
            }

            long nonNullPings = pings.stream().filter(Objects::nonNull).count();
            if (nonNullPings == 0) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Batch must contain at least one valid ping");
                return false;
            }

            for (TelemetryPingDTO p : pings) {
                if (p == null || p.getDeviceId() == null || p.getDeviceId().isBlank()) {
                    response.sendError(HttpStatus.BAD_REQUEST.value(), "Each ping must have a non-null deviceId");
                    return false;
                }
            }

            batchSize = (int) nonNullPings;
            if (batchSize > RateLimitingService.MAX_BATCH_SIZE) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Batch exceeds maximum size of " + RateLimitingService.MAX_BATCH_SIZE);
                return false;
            }

            Set<String> deviceIds = pings.stream()
                    .filter(Objects::nonNull)
                    .map(TelemetryPingDTO::getDeviceId)
                    .collect(Collectors.toSet());

            if (deviceIds.size() != 1) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Batch must contain pings for a single deviceId");
                return false;
            }

            deviceId = deviceIds.iterator().next();
        } else {
            TelemetryPingDTO pingDTO;
            try {
                pingDTO = objectMapper.readValue(requestBody, TelemetryPingDTO.class);
            } catch (IOException ex) {
                log.warn("[RATE LIMIT] Malformed ping JSON", ex);
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Malformed JSON in request body");
                return false;
            } catch (RuntimeException ex) {
                log.warn("[RATE LIMIT] Invalid ping payload", ex);
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid ping payload");
                return false;
            }

            if (pingDTO == null || pingDTO.getDeviceId() == null || pingDTO.getDeviceId().isBlank()) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "Missing deviceId in request body");
                return false;
            }

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
            log.warn("[RATE LIMIT] Device {} exceeded rate limit for {} pings!", deviceId, batchSize);
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
