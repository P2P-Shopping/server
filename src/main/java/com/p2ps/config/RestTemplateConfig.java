package com.p2ps.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * Used by OsrmClient for HTTP requests to the OSRM routing API.
     * ObjectMapper is already declared in JacksonConfig — not repeated here.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
