package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.ai.core.AiTool;
import com.p2ps.exception.AiProcessingException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Client for OpenAI-compatible APIs.
 */
public class OpenAiAiClient implements AiClient {

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiAiClient(String apiKey, String apiUrl, String model, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiMessage generateResponse(List<AiMessage> messages, List<AiTool> tools) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages.stream().map(this::mapToOpenAiMessage).collect(Collectors.toList()));
            
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", tools.stream().map(this::mapToOpenAiTool).collect(Collectors.toList()));
                requestBody.put("tool_choice", "auto");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            return parseOpenAiResponse(response.getBody());
        } catch (Exception e) {
            throw new AiProcessingException("OpenAI API error: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> mapToOpenAiMessage(AiMessage message) {
        Map<String, Object> map = new HashMap<>();
        
        // Map roles: model -> assistant, function -> tool
        String role = message.role();
        if ("model".equalsIgnoreCase(role)) role = "assistant";
        if ("function".equalsIgnoreCase(role)) role = "tool";
        
        map.put("role", role);

        List<Map<String, Object>> content = new ArrayList<>();
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        for (AiMessage.Part part : message.parts()) {
            if (part instanceof AiMessage.TextPart textPart) {
                content.add(Map.of("type", "text", "text", textPart.text()));
            } else if (part instanceof AiMessage.ImagePart imagePart) {
                String base64 = Base64.getEncoder().encodeToString(imagePart.data());
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:" + imagePart.mimeType() + ";base64," + base64)
                ));
            } else if (part instanceof AiMessage.ToolCallPart toolCallPart) {
                // In OpenAI, tool calls are separate from content
                toolCalls.add(Map.of(
                        "id", "call_" + toolCallPart.name() + "_" + UUID.randomUUID().toString().substring(0, 8),
                        "type", "function",
                        "function", Map.of(
                                "name", toolCallPart.name(),
                                "arguments", serializeArgs(toolCallPart.arguments())
                        )
                ));
            } else if (part instanceof AiMessage.ToolResponsePart toolResponsePart) {
                // For 'tool' role, we need tool_call_id
                map.put("tool_call_id", "call_" + toolResponsePart.name()); // This is a bit tricky since we don't track IDs perfectly yet
                map.put("content", String.valueOf(toolResponsePart.content()));
                return map;
            }
        }

        if (!content.isEmpty()) {
            map.put("content", content);
        }
        if (!toolCalls.isEmpty()) {
            map.put("tool_calls", toolCalls);
        }

        return map;
    }

    private String serializeArgs(Map<String, Object> args) {
        try {
            return objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> mapToOpenAiTool(AiTool tool) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "parameters", tool.parameters()
                )
        );
    }

    private AiMessage parseOpenAiResponse(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode choice = rootNode.path("choices").get(0);
        JsonNode messageNode = choice.path("message");
        
        String role = messageNode.path("role").asText("assistant");
        List<AiMessage.Part> parts = new ArrayList<>();

        if (messageNode.has("content") && !messageNode.get("content").isNull()) {
            parts.add(new AiMessage.TextPart(messageNode.get("content").asText()));
        }

        if (messageNode.has("tool_calls")) {
            for (JsonNode tc : messageNode.get("tool_calls")) {
                String name = tc.path("function").path("name").asText();
                String argsStr = tc.path("function").path("arguments").asText();
                Map<String, Object> args = objectMapper.readValue(argsStr, Map.class);
                parts.add(new AiMessage.ToolCallPart(name, args));
            }
        }

        return new AiMessage(role, parts);
    }
}
