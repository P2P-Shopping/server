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

    @Test
    void calculateOptimalRoute_shouldStoreResultInCacheUsingRequestHashCode() {
        RoutingRequest request = new RoutingRequest(10.5, 20.5, List.of("item_101", "item_102"));

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

        assertNotNull(cache.get("null"));
        assertEquals(first, cache.get("null", RoutingResponse.class));
    }
}