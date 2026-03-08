package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class PromptContentDTO {
    private String promptCode;
    private String name;
    private String systemPrompt;
    private String userPromptTemplate;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Boolean enableDeepThinking;
    private Integer version;
    private Long updatedBy;
}
