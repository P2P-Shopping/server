package com.p2ps.ai.service;

import com.p2ps.exception.AiProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiServiceTest {

    private GeminiService geminiService;
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() throws Exception {
        geminiService = new GeminiService();
        restTemplate = mock(RestTemplate.class);

        Field restField = GeminiService.class.getDeclaredField("restTemplate");
        restField.setAccessible(true);
        restField.set(geminiService, restTemplate);

        Field apiUrlField = GeminiService.class.getDeclaredField("apiUrl");
        apiUrlField.setAccessible(true);
        apiUrlField.set(geminiService, "http://fake");

        Field apiKeyField = GeminiService.class.getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        apiKeyField.set(geminiService, "key");
    }

    @Test
    void whenRestThrows_shouldWrapInAiProcessingException() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenThrow(new RuntimeException("conn"));
        assertThatThrownBy(() -> geminiService.extractIngredientsAsJson("text"))
            .isInstanceOf(AiProcessingException.class)
            .hasMessageContaining("Could not communicate with Google Gemini API");
    }

    @Test
    void whenNoCandidates_shouldThrow() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ResponseEntity.ok("{\"candidates\":[]}"));
        assertThatThrownBy(() -> geminiService.extractIngredientsAsJson("text"))
            .isInstanceOf(AiProcessingException.class)
            .hasMessageContaining("Google API returned an empty response or blocked the request.");
    }

    @Test
    void success_returnsText() {
        String inner = "[{\"name\":\"Tomato\",\"quantity\":2,\"unit\":\"pieces\"}]";
        String resp = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + inner.replace("\"","\\\"") + "\"}]}}]}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ResponseEntity.ok(resp));
        String result = geminiService.extractIngredientsAsJson("text");
        assertThat(result).isEqualTo(inner);
    }
}
