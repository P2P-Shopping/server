package com.p2ps.ai.core;

import java.util.List;

public interface AiClient {
    AiMessage generateResponse(List<AiMessage> messages, List<AiTool> tools);
}
