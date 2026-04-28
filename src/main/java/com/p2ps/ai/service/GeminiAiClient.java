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

public class GeminiAiClient implements AiClient {

    private final String apiKey;
    private final String apiUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiAiClient(String apiKey, String apiUrl, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiMessage generateResponse(List<AiMessage> messages, List<AiTool> tools) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", messages.stream().map(this::mapToGeminiContent).collect(Collectors.toList()));
            
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", List.of(Map.of("function_declarations", tools.stream().map(this::mapToGeminiTool).collect(Collectors.toList()))));
            }
            
            requestBody.put("generationConfig", Map.of("responseMimeType", "application/json"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            return parseGeminiResponse(response.getBody());
        } catch (Exception e) {
            throw new AiProcessingException("Gemini API error: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> mapToGeminiContent(AiMessage message) {
        List<Map<String, Object>> parts = message.parts().stream().<Map<String, Object>>map(part -> {
            if (part instanceof AiMessage.TextPart textPart) {
                return Map.of("text", textPart.text());
            } else if (part instanceof AiMessage.ImagePart imagePart) {
                return Map.of("inlineData", Map.of(
                        "mimeType", imagePart.mimeType(),
                        "data", Base64.getEncoder().encodeToString(imagePart.data())
                ));
            } else if (part instanceof AiMessage.ToolCallPart toolCallPart) {
                return Map.of("functionCall", Map.of(
                        "name", toolCallPart.name(),
                        "args", toolCallPart.arguments()
                ));
            } else if (part instanceof AiMessage.ToolResponsePart toolResponsePart) {
                return Map.of("functionResponse", Map.of(
                        "name", toolResponsePart.name(),
                        "response", Map.of("content", toolResponsePart.content())
                ));
            }
            return Collections.<String, Object>emptyMap();
        }).collect(Collectors.toList());

        return Map.of("role", message.role(), "parts", parts);
    }

    private Map<String, Object> mapToGeminiTool(AiTool tool) {
        return Map.of(
                "name", tool.name(),
                "description", tool.description(),
                "parameters", tool.parameters()
        );
    }

    private AiMessage parseGeminiResponse(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode candidate = rootNode.path("candidates").get(0);
        JsonNode content = candidate.path("content");
        String role = content.path("role").asText("model");
        JsonNode partsNode = content.path("parts");

        List<AiMessage.Part> parts = new ArrayList<>();
        for (JsonNode partNode : partsNode) {
            if (partNode.has("text")) {
                parts.add(new AiMessage.TextPart(partNode.get("text").asText()));
            } else if (partNode.has("functionCall")) {
                JsonNode fc = partNode.get("functionCall");
                String name = fc.get("name").asText();
                Map<String, Object> args = objectMapper.convertValue(fc.get("args"), Map.class);
                parts.add(new AiMessage.ToolCallPart(name, args));
            }
        }

        return new AiMessage(role, parts);
    }
}
