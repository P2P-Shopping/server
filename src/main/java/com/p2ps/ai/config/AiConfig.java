package com.p2ps.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.service.GeminiAiClient;
import com.p2ps.ai.service.OpenAiAiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiConfig {

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.model:gemini-2.5-flash-lite}")
    private String model;

    @Value("${ai.provider:gemini}")
    private String provider;

    @Bean
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

    @Bean
    public AiClient aiClient(RestTemplate aiRestTemplate, ObjectMapper objectMapper) {
        if ("gemini".equalsIgnoreCase(provider)) {
            return new GeminiAiClient(apiKey, apiUrl, aiRestTemplate, objectMapper);
        }
        // Default to OpenAI-compatible client for other providers
        return new OpenAiAiClient(apiKey, apiUrl, model, aiRestTemplate, objectMapper);
    }
}
