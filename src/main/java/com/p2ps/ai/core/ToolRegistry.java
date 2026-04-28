package com.p2ps.ai.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {
    private final Map<String, AiTool> tools = new HashMap<>();

    public void register(AiTool tool) {
        tools.put(tool.name(), tool);
    }

    public List<AiTool> getAvailableTools() {
        return new ArrayList<>(tools.values());
    }

    public Object executeTool(String name, Map<String, Object> arguments, Map<String, Object> context) {
        AiTool tool = tools.get(name);
        if (tool == null) {
            return "Error: Tool not found: " + name;
        }
        try {
            return tool.executor().apply(arguments, context);
        } catch (Exception e) {
            return "Error executing tool " + name + ": " + e.getMessage();
        }
    }
}
