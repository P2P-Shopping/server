package com.p2ps.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2ps.ai.core.AiClient;
import com.p2ps.ai.core.AiMessage;
import com.p2ps.ai.core.AiTool;
import com.p2ps.exception.AiProcessingException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiAiClient implements AiClient {
    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s\"");

    private final String apiKey;
    private final String apiUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String FUNCTION_CALL = "functionCall";

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
            requestBody.put("contents", messages.stream().map(this::mapToGeminiContent).toList());
            
            Map<String, Object> generationConfig = new HashMap<>();
            
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", List.of(Map.of("function_declarations", tools.stream().map(this::mapToGeminiTool).toList())));
            } else {
                generationConfig.put("responseMimeType", "application/json");
            }
            
            if (!generationConfig.isEmpty()) {
                requestBody.put("generationConfig", generationConfig);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            return parseGeminiResponse(response.getBody());
        } catch (HttpClientErrorException.TooManyRequests e) {
            long retryAfterSeconds = extractRetryAfterSeconds(e.getResponseBodyAsString());
            String message = retryAfterSeconds > 0
                    ? "Gemini quota exceeded. Please retry in about " + retryAfterSeconds + " seconds."
                    : "Gemini quota exceeded. Please retry later or check your Gemini billing/quota.";
            throw new AiProcessingException(message, e, HttpStatus.TOO_MANY_REQUESTS, retryAfterSeconds > 0 ? retryAfterSeconds : null);
        } catch (HttpClientErrorException e) {
            throw new AiProcessingException(
                    "Gemini API error: " + e.getStatusCode() + " " + e.getStatusText(),
                    e,
                    HttpStatus.valueOf(e.getStatusCode().value())
            );
        } catch (Exception e) {
            throw new AiProcessingException("Gemini API error: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> mapToGeminiContent(AiMessage message) {
        List<Map<String, Object>> parts = message.parts().stream().<Map<String, Object>>map(part -> {
            if (part instanceof AiMessage.TextPart(String text)) {
                return Map.of("text", text);
            } else if (part instanceof AiMessage.ImagePart(byte[] data, String mimeType)) {
                return Map.of("inlineData", Map.of(
                        "mimeType", mimeType,
                        "data", Base64.getEncoder().encodeToString(data)
                ));
            } else if (part instanceof AiMessage.ToolCallPart(String name, Map<String, Object> arguments)) {
                return Map.of(FUNCTION_CALL, Map.of(
                        "name", name,
                        "args", arguments
                ));
            } else if (part instanceof AiMessage.ToolResponsePart(String name, Object content)) {
                return Map.of("functionResponse", Map.of(
                        "name", name,
                        "response", Map.of("content", content)
                ));
            }
            return Collections.<String, Object>emptyMap();
        }).toList();

        return Map.of("role", message.role(), "parts", parts);
    }

    private Map<String, Object> mapToGeminiTool(AiTool tool) {
        return Map.of(
                "name", tool.name(),
                "description", tool.description(),
                "parameters", tool.parameters()
        );
    }

    private AiMessage parseGeminiResponse(String responseBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode candidate = rootNode.path("candidates").get(0);
            JsonNode content = candidate.path("content");
            String role = content.path("role").asText("model");
            JsonNode partsNode = content.path("parts");

            List<AiMessage.Part> parts = new ArrayList<>();
            for (JsonNode partNode : partsNode) {
                if (partNode.has("text")) {
                    parts.add(new AiMessage.TextPart(partNode.get("text").asText()));
                } else if (partNode.has(FUNCTION_CALL)) {
                    JsonNode fc = partNode.get(FUNCTION_CALL);
                    String name = fc.get("name").asText();
                    Map<String, Object> args = objectMapper.convertValue(fc.get("args"), Map.class);
                    parts.add(new AiMessage.ToolCallPart(name, args));
                }
            }

            return new AiMessage(role, parts);
        } catch (Exception e) {
            throw new AiProcessingException("Failed to parse Gemini response", e);
        }
    }

    private long extractRetryAfterSeconds(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return -1;
        }

        Matcher matcher = RETRY_DELAY_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }

        return -1;
    }
}
