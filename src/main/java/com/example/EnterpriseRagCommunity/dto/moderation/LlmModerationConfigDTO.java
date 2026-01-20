package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LlmModerationConfigDTO {
    private Long id;
    private Integer version;

    private String promptTemplate;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Double threshold;
    private Boolean autoRun;

    // LLM auto runner throttling
    private Integer maxConcurrent;
    private Integer minDelayMs;
    private Double qps;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
