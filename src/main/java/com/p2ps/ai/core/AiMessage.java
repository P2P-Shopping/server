package com.p2ps.ai.core;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AiMessage(
    String role, // "user", "model", "tool"
    List<Part> parts
) {
    public interface Part {}
    public record TextPart(String text) implements Part {}
    public record ImagePart(byte[] data, String mimeType) implements Part {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ImagePart that = (ImagePart) o;
            return Arrays.equals(data, that.data) && Objects.equals(mimeType, that.mimeType);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(data);
            result = 31 * result + Objects.hashCode(mimeType);
            return result;
        }

        @Override
        public String toString() {
            return "ImagePart[" +
                    "data=" + Arrays.toString(data) +
                    ", mimeType=" + mimeType +
                    "]";
        }
    }
    public record ToolCallPart(String name, Map<String, Object> arguments) implements Part {}
    public record ToolResponsePart(String name, Object content) implements Part {}
}
