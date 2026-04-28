package com.p2ps.ai.core;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Represents a tool that the AI can call.
 * The executor takes (toolArguments, executionContext) and returns the tool output.
 */
public record AiTool(
    String name,
    String description,
    Map<String, Object> parameters,
    BiFunction<Map<String, Object>, Map<String, Object>, Object> executor
) {}
