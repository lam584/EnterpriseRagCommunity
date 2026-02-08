package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class AssistantPreferencesDTO {
    private String defaultProviderId;
    private String defaultModel;
    private boolean defaultDeepThink;
    private boolean autoLoadLastSession;
    private boolean defaultUseRag;
    private int ragTopK;
    private boolean stream;
    private Double temperature;
    private Double topP;
    private String defaultSystemPrompt;
}
