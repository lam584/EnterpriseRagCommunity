package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public final class ChatMessageSupport {

    private ChatMessageSupport() {
    }

    public static List<ChatMessage> buildSystemUserMessages(String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));
        return messages;
    }
}
