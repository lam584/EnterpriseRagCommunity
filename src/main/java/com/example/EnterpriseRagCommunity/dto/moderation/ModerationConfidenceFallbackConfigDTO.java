package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModerationConfidenceFallbackConfigDTO {

    private Long id;
    private Integer version;

    // RULE
    private Boolean ruleEnabled;
    private ModerationConfidenceFallbackConfigEntity.Action ruleHighAction;
    private ModerationConfidenceFallbackConfigEntity.Action ruleMediumAction;
    private ModerationConfidenceFallbackConfigEntity.Action ruleLowAction;

    // VEC
    private Boolean vecEnabled;
    private Double vecThreshold;
    private ModerationConfidenceFallbackConfigEntity.Action vecHitAction;
    private ModerationConfidenceFallbackConfigEntity.Action vecMissAction;

    // LLM
    private Boolean llmEnabled;
    private Double llmRejectThreshold;
    private Double llmHumanThreshold;

    private Integer reportHumanThreshold;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
