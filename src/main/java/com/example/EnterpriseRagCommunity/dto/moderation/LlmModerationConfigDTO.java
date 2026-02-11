package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LlmModerationConfigDTO {
    private Long id;
    private Integer version;

    private String promptTemplate;
    private String visionPromptTemplate;
    private String model;
    private String providerId;
    private String visionModel;
    private String visionProviderId;
    private Double temperature;
    private Double topP;
    private Double visionTemperature;
    private Double visionTopP;
    private Integer maxTokens;
    private Integer visionMaxTokens;
    private Boolean enableThinking;
    private Boolean visionEnableThinking;
    private Double threshold;
    private Boolean autoRun;

    // LLM auto runner throttling
    private Integer maxConcurrent;
    private Integer minDelayMs;
    private Double qps;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
