package com.p2ps.ai.core;

import java.util.List;
import java.util.Map;

public record AiMessage(
    String role, // "user", "model", "tool"
    List<Part> parts
) {
    public interface Part {}
    public record TextPart(String text) implements Part {}
    public record ImagePart(byte[] data, String mimeType) implements Part {}
    public record ToolCallPart(String name, Map<String, Object> arguments) implements Part {}
    public record ToolResponsePart(String name, Object content) implements Part {}
}
