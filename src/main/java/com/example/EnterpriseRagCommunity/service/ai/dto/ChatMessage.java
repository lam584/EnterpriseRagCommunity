package com.example.EnterpriseRagCommunity.service.ai.dto;

import java.util.List;
import java.util.Map;

public record ChatMessage(String role, Object content) {
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage userParts(List<Map<String, Object>> parts) {
        return new ChatMessage("user", parts);
    }
}
