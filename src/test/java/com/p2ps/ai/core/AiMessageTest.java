package com.p2ps.ai.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiMessageTest {

    @Test
    void textPart_recordFields() {
        AiMessage.TextPart part = new AiMessage.TextPart("Hello");
        assertThat(part.text()).isEqualTo("Hello");
    }

    @Test
    void imagePart_recordFields() {
        byte[] data = "image".getBytes();
        AiMessage.ImagePart part = new AiMessage.ImagePart(data, "image/png");
        assertThat(part.data()).isEqualTo(data);
        assertThat(part.mimeType()).isEqualTo("image/png");
    }

    @Test
    void toolCallPart_recordFields() {
        Map<String, Object> args = Map.of("key", "value");
        AiMessage.ToolCallPart part = new AiMessage.ToolCallPart("search", args);
        assertThat(part.name()).isEqualTo("search");
        assertThat(part.arguments()).isEqualTo(args);
    }

    @Test
    void toolResponsePart_recordFields() {
        AiMessage.ToolResponsePart part = new AiMessage.ToolResponsePart("search", "result-data");
        assertThat(part.name()).isEqualTo("search");
        assertThat(part.content()).isEqualTo("result-data");
    }

    @Test
    void aiMessage_recordFields() {
        AiMessage.TextPart part = new AiMessage.TextPart("Hi");
        AiMessage message = new AiMessage("user", List.of(part));
        assertThat(message.role()).isEqualTo("user");
        assertThat(message.parts()).hasSize(1);
        assertThat(message.parts().get(0)).isInstanceOf(AiMessage.TextPart.class);
    }

    @Test
    void aiMessage_withMultipleParts() {
        AiMessage.TextPart text = new AiMessage.TextPart("text");
        AiMessage.ImagePart image = new AiMessage.ImagePart("img".getBytes(), "image/jpeg");
        AiMessage message = new AiMessage("user", List.of(text, image));
        assertThat(message.parts()).hasSize(2);
    }

    @Test
    void partInterface_implementedByAllParts() {
        AiMessage.Part textPart = new AiMessage.TextPart("t");
        AiMessage.Part imagePart = new AiMessage.ImagePart(new byte[0], "img");
        AiMessage.Part toolCallPart = new AiMessage.ToolCallPart("n", Map.of());
        AiMessage.Part toolResponsePart = new AiMessage.ToolResponsePart("n", "c");

        assertThat(textPart).isInstanceOf(AiMessage.Part.class);
        assertThat(imagePart).isInstanceOf(AiMessage.Part.class);
        assertThat(toolCallPart).isInstanceOf(AiMessage.Part.class);
        assertThat(toolResponsePart).isInstanceOf(AiMessage.Part.class);
    }

    @Test
    void imagePart_equals_sameObject() {
        byte[] data = "image".getBytes();
        AiMessage.ImagePart part = new AiMessage.ImagePart(data, "image/png");
        assertThat(part.equals(part)).isTrue();
    }

    @Test
    void imagePart_equals_equalObjects() {
        byte[] data = "image".getBytes();
        AiMessage.ImagePart part1 = new AiMessage.ImagePart(data, "image/png");
        AiMessage.ImagePart part2 = new AiMessage.ImagePart(data.clone(), "image/png");
        assertThat(part1).isEqualTo(part2);
    }

    @Test
    void imagePart_equals_differentData() {
        AiMessage.ImagePart part1 = new AiMessage.ImagePart("image1".getBytes(), "image/png");
        AiMessage.ImagePart part2 = new AiMessage.ImagePart("image2".getBytes(), "image/png");
        assertThat(part1).isNotEqualTo(part2);
    }

    @Test
    void imagePart_equals_differentMimeType() {
        byte[] data = "image".getBytes();
        AiMessage.ImagePart part1 = new AiMessage.ImagePart(data, "image/png");
        AiMessage.ImagePart part2 = new AiMessage.ImagePart(data.clone(), "image/jpeg");
        assertThat(part1).isNotEqualTo(part2);
        assertThat(part2).isNotEqualTo(part1);
    }

    @Test
    void imagePart_equals_null() {
        AiMessage.ImagePart part = new AiMessage.ImagePart("image".getBytes(), "image/png");
        assertThat(part.equals(null)).isFalse();
    }

    @Test
    void imagePart_equals_differentType() {
        AiMessage.ImagePart part = new AiMessage.ImagePart("image".getBytes(), "image/png");
        assertThat(part.equals("string")).isFalse();
    }

    @Test
    void imagePart_hashCode_equalObjects() {
        byte[] data = "image".getBytes();
        AiMessage.ImagePart part1 = new AiMessage.ImagePart(data, "image/png");
        AiMessage.ImagePart part2 = new AiMessage.ImagePart(data.clone(), "image/png");
        assertThat(part1).hasSameHashCodeAs(part2);
    }

    @Test
    void imagePart_toString_containsDataAndMimeType() {
        AiMessage.ImagePart part = new AiMessage.ImagePart("image".getBytes(), "image/png");
        String str = part.toString();
        assertThat(str).contains("data=", "mimeType=", "image/png");
    }

    @Test
    void imagePart_toString_emptyData() {
        AiMessage.ImagePart part = new AiMessage.ImagePart(new byte[0], "image/png");
        String str = part.toString();
        assertThat(str).contains("data=", "mimeType=image/png");
    }
}
