package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ModerationConfidenceFallbackConfigDTO {

    private Long id;
    private Integer version;

    // LLM
    private Boolean llmEnabled;
    private Double llmRejectThreshold;
    private Double llmHumanThreshold;

    private Double chunkLlmRejectThreshold;
    private Double chunkLlmHumanThreshold;

    private Double llmTextRiskThreshold;
    private Double llmImageRiskThreshold;
    private Double llmStrongRejectThreshold;
    private Double llmStrongPassThreshold;
    private Double llmCrossModalThreshold;

    private Integer reportHumanThreshold;

    private Integer chunkThresholdChars;

    private Map<String, Object> thresholds;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
