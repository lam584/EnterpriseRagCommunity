package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class PortalChatConfigDTO {
    private AssistantChatConfigDTO assistantChat;
    private PostComposeAssistantConfigDTO postComposeAssistant;

    @Data
    public static class AssistantChatConfigDTO {
        private String providerId;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer historyLimit;
        private Boolean defaultDeepThink;
        private Boolean defaultUseRag;
        private Integer ragTopK;
        private Boolean defaultStream;
        private String systemPrompt;
        private String deepThinkSystemPrompt;
    }

    @Data
    public static class PostComposeAssistantConfigDTO {
        private String providerId;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer chatHistoryLimit;
        private Boolean defaultDeepThink;
        private String systemPrompt;
        private String deepThinkSystemPrompt;
        private String composeSystemPrompt;
    }
}

