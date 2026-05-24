package com.p2ps.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * Used by OsrmClient for HTTP requests to the OSRM routing API.
     * ObjectMapper is already declared in JacksonConfig — not repeated here.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 seconds
        factory.setReadTimeout(60000);    // 60 seconds – Overpass & OSRM can be slow
        return new RestTemplate(factory);
    }
}
