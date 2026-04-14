package com.p2ps.telemetry.services;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitingService {

    private final Cache<String, Bucket> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    public Bucket resolveBucket(String deviceId) {
        return cache.get(deviceId, this::newBucket);
    }

    private Bucket newBucket(String ignoredDeviceId) {
        // Maximum of 10 requests per second
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofSeconds(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
