package com.p2ps.test;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a Redis container before the Spring context is refreshed and
 * exposes its host/port to the Spring Environment via dynamic properties.
 */
public class RedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.2.6");
    private static GenericContainer<?> redisContainer;

    private static synchronized void startContainerIfNeeded() {
        if (redisContainer == null) {
            redisContainer = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
            redisContainer.start();
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        startContainerIfNeeded();

        String host = redisContainer.getHost();
        Integer port = redisContainer.getMappedPort(6379);

        TestPropertyValues.of(
                "spring.redis.host=" + host,
                "spring.redis.port=" + port
        ).applyTo(context.getEnvironment());
    }
}
