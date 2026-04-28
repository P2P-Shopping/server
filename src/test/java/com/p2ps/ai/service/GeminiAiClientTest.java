package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.ai.core.AiTool;
import com.p2ps.exception.AiProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiAiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private GeminiAiClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        client = new GeminiAiClient("test-key", "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent", restTemplate, objectMapper);
    }

    @Test
    void generateResponse_textOnly_returnsTextPart() throws Exception {
        String responseJson = """
                {
                  "candidates": [{
                    "content": {
                      "role": "model",
                      "parts": [{"text": "Hello from Gemini"}]
                    }
                  }]
                }
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiMessage response = client.generateResponse(List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Hi")))), null);

        assertThat(response.role()).isEqualTo("model");
        assertThat(response.parts()).hasSize(1);
        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Hello from Gemini");
    }

    @Test
    void generateResponse_withTools_includesFunctionDeclarations() throws Exception {
        String responseJson = """
                {
                  "candidates": [{
                    "content": {
                      "role": "model",
                      "parts": [{"functionCall": {"name": "search_catalog", "args": {"keyword": "milk"}}}]
                    }
                  }]
                }
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiTool tool = new AiTool("search_catalog", "Search catalog", Map.of("type", "OBJECT"), (args, ctx) -> "result");
        AiMessage response = client.generateResponse(List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Find milk")))), List.of(tool));

        assertThat(response.parts()).hasSize(1);
        AiMessage.ToolCallPart toolCall = (AiMessage.ToolCallPart) response.parts().get(0);
        assertThat(toolCall.name()).isEqualTo("search_catalog");
        assertThat(toolCall.arguments()).containsEntry("keyword", "milk");
    }

    @Test
    void generateResponse_imagePart_mappedCorrectly() throws Exception {
        byte[] imageData = "image-bytes".getBytes();
        String responseJson = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Image processed"}]}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiMessage response = client.generateResponse(
                List.of(new AiMessage("user", List.of(new AiMessage.ImagePart(imageData, "image/jpeg")))), null);

        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Image processed");
    }

    @Test
    void generateResponse_apiError_throwsAiProcessingException() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        List<AiMessage> messages = List.of();
        assertThatThrownBy(() -> client.generateResponse(messages, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("Gemini API error");
    }

    @Test
    void generateResponse_toolResponsePart_mappedCorrectly() throws Exception {
        String responseJson = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Done"}]}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiMessage response = client.generateResponse(
                List.of(new AiMessage("tool", List.of(new AiMessage.ToolResponsePart("search", "result")))), null);

        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Done");
    }

    @Test
    void generateResponse_emptyParts_returnsEmptyPartsList() throws Exception {
        String responseJson = """
                {"candidates":[{"content":{"role":"model","parts":[]}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiMessage response = client.generateResponse(List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Hi")))), null);

        assertThat(response.parts()).isEmpty();
    }

    @Test
    void generateResponse_withoutTools_requestsJsonMimeType() throws Exception {
        String responseJson = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"{}"}]}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        client.generateResponse(List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Return JSON")))), List.of());

        verify(restTemplate).postForEntity(any(String.class), any(HttpEntity.class), eq(String.class));
    }
}
