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
}
