package com.p2ps.telemetry.services;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RateLimitingServiceTest {

    @Test
    void shouldReuseBucketForSameDeviceId() {
        RateLimitingService service = new RateLimitingService();

        Bucket first = service.resolveBucket("device-1");
        Bucket second = service.resolveBucket("device-1");

        assertSame(first, second);
    }

    @Test
    void shouldCreateDifferentBucketsForDifferentDeviceIds() {
        RateLimitingService service = new RateLimitingService();

        Bucket first = service.resolveBucket("device-1");
        Bucket second = service.resolveBucket("device-2");

        assertNotSame(first, second);
    }
}
