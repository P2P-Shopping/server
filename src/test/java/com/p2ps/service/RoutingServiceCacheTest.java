package com.p2ps.service;

import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Testcontainers
@SpringBootTest(classes = RoutingServiceCacheTest.TestConfig.class)
@ActiveProfiles("test")
class RoutingServiceCacheTest {

    @TestConfiguration
    @EnableCaching
    static class TestConfig {
        @Bean
        RoutingService routingService() {
            return new RoutingService();
        }
    }

    @Autowired
    private RoutingService routingService;

    @Autowired
    private CacheManager cacheManager;

    @Container
    @SuppressWarnings("resource")
    public static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.6").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        // Support both spring.redis.* and spring.data.redis.* property keys used in this project
        registry.add("spring.redis.host", () -> redis.getHost());
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    void calculateOptimalRoute_shouldStoreResultInCacheUsingRequestHashCode() {
        RoutingRequest request = new RoutingRequest();
        request.setUserLat(10.5);
        request.setUserLng(20.5);
        request.setProductIds(List.of("item_101", "item_102"));

        RoutingResponse first = routingService.calculateOptimalRoute(request);
        RoutingResponse second = routingService.calculateOptimalRoute(request);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first, second);

        Cache cache = cacheManager.getCache("routes");
        assertNotNull(cache);

        assertNotNull(cache.get(request.hashCode()));
        assertEquals(first, cache.get(request.hashCode(), RoutingResponse.class));
    }

    @Test
    void calculateOptimalRoute_shouldCacheNullRequestUnderNullKey() {
        RoutingResponse first = routingService.calculateOptimalRoute(null);
        RoutingResponse second = routingService.calculateOptimalRoute(null);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first, second);

        Cache cache = cacheManager.getCache("routes");
        assertNotNull(cache);

        assertNotNull(cache.get(0));
        assertEquals(first, cache.get(0, RoutingResponse.class));
    }
}
