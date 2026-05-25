package com.p2ps.telemetry.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class TelemetryImportMongoConfig {

    @Value("${telemetry.raw-ping-import.fallback-mongo-uri:mongodb://localhost:27017/p2p_shopping_mongo}")
    private String fallbackMongoUri;

    @Bean(name = "fallbackMongoTemplate")
    public MongoTemplate fallbackMongoTemplate() {
        ConnectionString connectionString = new ConnectionString(fallbackMongoUri);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToSocketSettings(builder -> builder.connectTimeout(3000, TimeUnit.MILLISECONDS))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(3000, TimeUnit.MILLISECONDS))
                .build();

        MongoClient client = MongoClients.create(settings);
        SimpleMongoClientDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(client,
                connectionString.getDatabase());

        log.info("[TELEMETRY_IMPORT] Configured fallback MongoTemplate for local MongoDB: {}", fallbackMongoUri);
        return new MongoTemplate(factory);
    }
}
