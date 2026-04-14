package com.p2ps.test;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestContainerExtension implements BeforeAllCallback {
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.2.6");
    private static GenericContainer<?> redisContainer;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (redisContainer == null) {
            redisContainer = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
            redisContainer.start();
            System.setProperty("spring.redis.host", redisContainer.getHost());
            System.setProperty("spring.redis.port", String.valueOf(redisContainer.getMappedPort(6379)));
        }
    }
}
