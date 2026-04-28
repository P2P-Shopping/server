package com.p2ps.ai.core;

import java.util.List;
import java.util.Map;

public interface AiClient {
    AiMessage generateResponse(List<AiMessage> messages, List<AiTool> tools);
}
