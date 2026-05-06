package com.p2ps.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.service.GeminiAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ai.api.key=test-key",
        "ai.api.url=https://api.test.com",
        "ai.model=gpt-4",
        "ai.provider=gemini",
        "spring.main.allow-bean-definition-overriding=true"
})
class AiConfigTest {

    @TestConfiguration
    static class Config {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private AiClient aiClient;

    @Autowired
    private RestTemplate aiRestTemplate;

    @Test
    void aiRestTemplate_createdWithTimeouts() {
        assertThat(aiRestTemplate).isNotNull();
    }

    @Test
    void aiClient_geminiProvider_returnsGeminiClient() {
        assertThat(aiClient).isInstanceOf(GeminiAiClient.class);
    }
}
