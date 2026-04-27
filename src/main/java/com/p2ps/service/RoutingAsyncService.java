package com.p2ps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.controller.RoutePoint;
import com.p2ps.controller.RoutingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * BE 3.1 — background half of Lazy Routing.
 *
 * Called by RoutingService when lazyN > 0 and the store has more products than lazyN.
 * Runs 3-opt on the full NN route in a separate thread (@Async),
 * then stores the optimized result in Redis so the frontend can retrieve it via
 * GET /api/routing/full/{routeId}.
 *
 * No circular dependency: injects RouteOptimizer (not RoutingService).
 */
@Service
public class RoutingAsyncService {

    private static final Logger logger = LoggerFactory.getLogger(RoutingAsyncService.class);

    public static final String ROUTE_KEY_PREFIX = "route:";
    public static final String PENDING_KEY_PREFIX = "route:pending:";
    private static final Duration ROUTE_TTL = Duration.ofMinutes(30);
    public static final Duration PENDING_TTL = Duration.ofMinutes(5);

    private final RouteOptimizer optimizer;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RoutingAsyncService(RouteOptimizer optimizer,
                               StringRedisTemplate redis,
                               ObjectMapper objectMapper) {
        this.optimizer = optimizer;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs 3-opt on fullNnRoute in the background, stores the result in Redis.
     *
     * @param routeId    same UUID returned to the frontend in the partial response
     * @param fullNnRoute the complete NN route (user + all products, not trimmed)
     * @param warnings   warnings already collected during the eager phase
     */
    @Async("taskExecutor")
    public void completeRouteAsync(String routeId,
                                   List<RoutePoint> fullNnRoute,
                                   List<String> warnings) {
        logger.info("Background optimization started: routeId={} nodes={}", routeId, fullNnRoute.size());
        try {
            List<RoutePoint> optimized = optimizer.threeOptImprove(fullNnRoute);

            RoutingResponse fullResponse = RoutingResponse.full(routeId, optimized, warnings);
            String json = objectMapper.writeValueAsString(fullResponse);

            redis.opsForValue().set(ROUTE_KEY_PREFIX + routeId, json, ROUTE_TTL);
            logger.info("Background optimization complete: routeId={} saved to Redis", routeId);
        } catch (Exception e) {
            logger.error("Background optimization failed: routeId={}", routeId, e);
        }
    }
}
