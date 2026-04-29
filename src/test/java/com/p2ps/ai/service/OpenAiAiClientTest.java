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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiAiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private OpenAiAiClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        client = new OpenAiAiClient("test-key", "https://api.openai.com/v1/chat/completions", "gpt-4", restTemplate, objectMapper);
    }

    @Test
    void generateResponse_noTools_returnsTextMessage() throws Exception {
        String responseJson = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "Hello"
                    }
                  }]
                }
                """;
        when(restTemplate.postForEntity(eq("https://api.openai.com/v1/chat/completions"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiMessage response = client.generateResponse(List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Hi")))), null);

        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.parts()).hasSize(1);
        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Hello");
    }

    @Test
    void generateResponse_withTools_includesToolDefinitions() throws Exception {
        String responseJson = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "tool_calls": [{
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "search",
                          "arguments": "{}"
                        }
                      }]
                    }
                  }]
                }
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiTool tool = new AiTool("search", "Search", Map.of("type", "OBJECT"), (args, ctx) -> "result");
        AiMessage response = client.generateResponse(List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Find items")))), List.of(tool));

        assertThat(response.parts()).hasSize(1);
        assertThat(response.parts().get(0)).isInstanceOf(AiMessage.ToolCallPart.class);
    }

    @Test
    void generateResponse_imagePart_base64Encoded() throws Exception {
        byte[] imageData = "image-bytes".getBytes();
        String responseJson = """
                {"choices":[{"message":{"role":"assistant","content":"Image received"}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiMessage response = client.generateResponse(List.of(new AiMessage("user", List.of(new AiMessage.ImagePart(imageData, "image/png")))), null);

        assertThat(response.parts()).hasSize(1);
        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Image received");
    }

    @Test
    void generateResponse_apiError_throwsAiProcessingException() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("API down"));

        List<AiMessage> emptyList = List.of();
        assertThatThrownBy(() -> client.generateResponse(emptyList, null))
                .isInstanceOf(AiProcessingException.class)
                .hasMessageContaining("OpenAI API error");
    }

    @Test
    void generateResponse_toolResponsePart_mapsCorrectly() throws Exception {
        String responseJson = """
                {"choices":[{"message":{"role":"assistant","content":"Done"}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiMessage response = client.generateResponse(
                List.of(new AiMessage("tool", List.of(new AiMessage.ToolResponsePart("search", "result")))), null);

        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Done");
    }

    @Test
    void generateResponse_modelRole_convertedToAssistant() throws Exception {
        String responseJson = """
                {"choices":[{"message":{"role":"assistant","content":"Hi"}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        AiMessage response = client.generateResponse(
                List.of(new AiMessage("model", List.of(new AiMessage.TextPart("test")))), null);

        assertThat(response.role()).isEqualTo("assistant");
    }
}
