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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

        List<AiMessage> messages = List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Hi"))));
        AiMessage response = client.generateResponse(messages, null);

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
        List<AiMessage> messages = List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Find milk"))));
        AiMessage response = client.generateResponse(messages, List.of(tool));

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

        List<AiMessage> messages = List.of(new AiMessage("user", List.of(new AiMessage.ImagePart(imageData, "image/jpeg"))));
        AiMessage response = client.generateResponse(messages, null);

        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Image processed");
    }

    @Test
    void generateResponse_apiError_throwsAiProcessingException() {
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        List<AiMessage> messages = Collections.emptyList();
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

        List<AiMessage> messages = List.of(new AiMessage("tool", List.of(new AiMessage.ToolResponsePart("search", "result"))));
        AiMessage response = client.generateResponse(messages, null);

        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Done");
    }

    @Test
    void generateResponse_emptyParts_returnsEmptyPartsList() throws Exception {
        String responseJson = """
                {"candidates":[{"content":{"role":"model","parts":[]}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        List<AiMessage> messages = List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Hi"))));
        AiMessage response = client.generateResponse(messages, null);

        assertThat(response.parts()).isEmpty();
    }

    @Test
    void generateResponse_withoutTools_requestsJsonMimeType() throws Exception {
        String responseJson = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"{}"}]}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        List<AiMessage> messages = List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Return JSON"))));
        client.generateResponse(messages, Collections.emptyList());

        verify(restTemplate).postForEntity(any(String.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void generateResponse_tooManyRequestsWithRetryDelay() {
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                null,
                "{\"error\":{\"details\":[{\"retryDelay\":\"60s\"}]}}".getBytes(),
                StandardCharsets.UTF_8
        );
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(exception);

        List<AiMessage> messages = Collections.emptyList();
        assertThatThrownBy(() -> client.generateResponse(messages, null))
                .isInstanceOf(AiProcessingException.class)
                .satisfies(ex -> {
                    AiProcessingException ape = (AiProcessingException) ex;
                    assertThat(ape.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ape.getRetryAfterSeconds()).isEqualTo(60L);
                });
    }

    @Test
    void generateResponse_tooManyRequestsWithoutRetryDelay() {
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                null,
                "{}".getBytes(),
                StandardCharsets.UTF_8
        );
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(exception);

        List<AiMessage> messages = Collections.emptyList();
        assertThatThrownBy(() -> client.generateResponse(messages, null))
                .isInstanceOf(AiProcessingException.class)
                .satisfies(ex -> {
                    AiProcessingException ape = (AiProcessingException) ex;
                    assertThat(ape.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ape.getRetryAfterSeconds()).isNull();
                });
    }

    @Test
    void generateResponse_httpClientError() {
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                null,
                "Bad Request".getBytes(),
                StandardCharsets.UTF_8
        );
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(exception);

        List<AiMessage> messages = Collections.emptyList();
        assertThatThrownBy(() -> client.generateResponse(messages, null))
                .isInstanceOf(AiProcessingException.class)
                .satisfies(ex -> {
                    AiProcessingException ape = (AiProcessingException) ex;
                    assertThat(ape.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void generateResponse_parseGeminiResponseWithFunctionCall() throws Exception {
        String responseJson = """
                {
                  "candidates": [{
                    "content": {
                      "role": "model",
                      "parts": [{"functionCall": {"name": "search", "args": {"query": "test"}}}]
                    }
                  }]
                }
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        List<AiMessage> messages = List.of(new AiMessage("user", List.of(new AiMessage.TextPart("Search"))));
        AiMessage response = client.generateResponse(messages, null);

        assertThat(response.parts()).hasSize(1);
        assertThat(response.parts().get(0)).isInstanceOf(AiMessage.ToolCallPart.class);
        AiMessage.ToolCallPart toolCall = (AiMessage.ToolCallPart) response.parts().get(0);
        assertThat(toolCall.name()).isEqualTo("search");
        assertThat(toolCall.arguments()).isEqualTo(Map.of("query", "test"));
    }

    @Test
    void extractRetryAfterSeconds_shouldReturnDelay() throws Exception {
        java.lang.reflect.Method method = GeminiAiClient.class.getDeclaredMethod("extractRetryAfterSeconds", String.class);
        method.setAccessible(true);

        String responseBody = "{\"error\":{\"details\":[{\"retryDelay\":\"30s\"}]}}";
        long result = (long) method.invoke(client, responseBody);
        assertThat(result).isEqualTo(30L);
    }

    @Test
    void extractRetryAfterSeconds_shouldReturnNegativeOneWhenNull() throws Exception {
        java.lang.reflect.Method method = GeminiAiClient.class.getDeclaredMethod("extractRetryAfterSeconds", String.class);
        method.setAccessible(true);

        long result = (long) method.invoke(client, (String) null);
        assertThat(result).isEqualTo(-1L);
    }

    @Test
    void extractRetryAfterSeconds_shouldReturnNegativeOneWhenBlank() throws Exception {
        java.lang.reflect.Method method = GeminiAiClient.class.getDeclaredMethod("extractRetryAfterSeconds", String.class);
        method.setAccessible(true);

        long result = (long) method.invoke(client, "");
        assertThat(result).isEqualTo(-1L);
    }

    @Test
    void extractRetryAfterSeconds_shouldReturnNegativeOneWhenNoMatch() throws Exception {
        java.lang.reflect.Method method = GeminiAiClient.class.getDeclaredMethod("extractRetryAfterSeconds", String.class);
        method.setAccessible(true);

        long result = (long) method.invoke(client, "no retry delay here");
        assertThat(result).isEqualTo(-1L);
    }

    @Test
    void mapToGeminiContent_withToolResponsePart() throws Exception {
        String responseJson = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Done"}]}}]}
                """;
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseJson));

        List<AiMessage> messages = List.of(new AiMessage("tool", List.of(new AiMessage.ToolResponsePart("search", "result"))));

        AiMessage response = client.generateResponse(messages, null);
        assertThat(response.parts()).hasSize(1);
        assertThat(((AiMessage.TextPart) response.parts().get(0)).text()).isEqualTo("Done");
    }
}
