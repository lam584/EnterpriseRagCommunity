package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class PortalChatConfigDTO {
    private AssistantChatConfigDTO assistantChat;
    private PostComposeAssistantConfigDTO postComposeAssistant;

    @Data
    public static class AssistantChatConfigDTO {
        private Boolean allowManualModelSelection;
        private String providerId;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer historyLimit;
        private Boolean defaultDeepThink;
        private Boolean defaultUseRag;
        private Integer ragTopK;
        private Boolean defaultStream;
        private String systemPromptCode;
        private String deepThinkSystemPromptCode;
    }

    @Data
    public static class PostComposeAssistantConfigDTO {
        private Boolean allowManualModelSelection;
        private String providerId;
        private String model;
        private Double temperature;
        private Double topP;
        private Integer chatHistoryLimit;
        private Boolean defaultDeepThink;
        private String systemPromptCode;
        private String deepThinkSystemPromptCode;
        private String composeSystemPromptCode;
    }
}

