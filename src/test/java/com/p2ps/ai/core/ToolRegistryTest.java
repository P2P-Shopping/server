package com.p2ps.ai.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    void register_and_getAvailableTools() {
        AiTool tool = new AiTool("search", "Search tool", Map.of("type", "OBJECT"), (args, ctx) -> "result");
        registry.register(tool);

        assertThat(registry.getAvailableTools()).hasSize(1);
        assertThat(registry.getAvailableTools().get(0).name()).isEqualTo("search");
    }

    @Test
    void executeTool_toolExists_returnsResult() {
        AiTool tool = new AiTool("search", "Search", Map.of(), (args, ctx) -> "Found: " + args.get("query"));
        registry.register(tool);

        Object result = registry.executeTool("search", Map.of("query", "milk"), Map.of());

        assertThat(result).isEqualTo("Found: milk");
    }

    @Test
    void executeTool_toolNotFound_returnsError() {
        Object result = registry.executeTool("nonexistent", Map.of(), Map.of());

        assertThat(result).isEqualTo("Error: Tool not found: nonexistent");
    }

    @Test
    void executeTool_executionError_returnsError() {
        AiTool tool = new AiTool("failing", "Failing tool", Map.of(), (args, ctx) -> {
            throw new RuntimeException("Tool crashed");
        });
        registry.register(tool);

        Object result = registry.executeTool("failing", Map.of(), Map.of());

        assertThat(result).isEqualTo("Error executing tool failing: Tool crashed");
    }

    @Test
    void executeTool_withContext_passesContextToExecutor() {
        AiTool tool = new AiTool("ctxTool", "Context tool", Map.of(), (args, ctx) -> {
            return "User: " + ctx.get("userEmail");
        });
        registry.register(tool);

        Object result = registry.executeTool("ctxTool", Map.of(), Map.of("userEmail", "test@test.com"));

        assertThat(result).isEqualTo("User: test@test.com");
    }

    @Test
    void getAvailableTools_returnsCopy_notOriginal() {
        AiTool tool = new AiTool("t", "T", Map.of(), (args, ctx) -> "ok");
        registry.register(tool);

        var tools = registry.getAvailableTools();
        tools.clear();

        assertThat(registry.getAvailableTools()).hasSize(1);
    }
}
