package com.p2ps.telemetry.services;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitingService {

    public static final int MAX_BATCH_SIZE = 1000;

    private final Cache<String, Bucket> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    public Bucket resolveBucket(String deviceId) {
        return cache.get(deviceId, this::newBucket);
    }

    private Bucket newBucket(String ignoredDeviceId) {
        // Maximum requests per second aligned with allowed batch size
        Bandwidth limit = Bandwidth.builder()
                .capacity(MAX_BATCH_SIZE)
                .refillGreedy(MAX_BATCH_SIZE, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
