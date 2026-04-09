package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class PromptContentUpdateRequest {
    private String name;
    private String systemPrompt;
    private String userPromptTemplate;
    private String visionProviderId;
    private String visionModel;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Boolean enableDeepThinking;
}
