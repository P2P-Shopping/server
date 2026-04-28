package com.p2ps.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.service.OpenAiAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "ai.api.key=test-key",
        "ai.api.url=https://api.openai.com/v1/chat/completions",
        "ai.model=gpt-4",
        "ai.provider=openai",
        "spring.main.allow-bean-definition-overriding=true"
})
class AiConfigOpenAiTest {

    @TestConfiguration
    static class Config {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private AiClient aiClient;

    @Test
    void aiClient_openaiProvider_returnsOpenAiClient() {
        assertThat(aiClient).isInstanceOf(OpenAiAiClient.class);
    }
}
