package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_confidence_fallback_config")
public class ModerationConfidenceFallbackConfigEntity {

    public enum Action {
        REJECT,
        LLM,
        HUMAN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ---- RULE ----
    @Column(name = "rule_enabled", nullable = false)
    private Boolean ruleEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_high_action", nullable = false, length = 16)
    private Action ruleHighAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_medium_action", nullable = false, length = 16)
    private Action ruleMediumAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_low_action", nullable = false, length = 16)
    private Action ruleLowAction;

    // ---- VEC ----
    @Column(name = "vec_enabled", nullable = false)
    private Boolean vecEnabled;

    @Column(name = "vec_threshold", nullable = false)
    private Double vecThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "vec_hit_action", nullable = false, length = 16)
    private Action vecHitAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "vec_miss_action", nullable = false, length = 16)
    private Action vecMissAction;

    // ---- LLM ----
    @Column(name = "llm_enabled", nullable = false)
    private Boolean llmEnabled;

    @Column(name = "llm_reject_threshold", nullable = false)
    private Double llmRejectThreshold;

    @Column(name = "llm_human_threshold", nullable = false)
    private Double llmHumanThreshold;

    @Column(name = "chunk_llm_reject_threshold", nullable = false)
    private Double chunkLlmRejectThreshold;

    @Column(name = "chunk_llm_human_threshold", nullable = false)
    private Double chunkLlmHumanThreshold;

    @Column(name = "llm_text_risk_threshold", nullable = false)
    private Double llmTextRiskThreshold;

    @Column(name = "llm_image_risk_threshold", nullable = false)
    private Double llmImageRiskThreshold;

    @Column(name = "llm_strong_reject_threshold", nullable = false)
    private Double llmStrongRejectThreshold;

    @Column(name = "llm_strong_pass_threshold", nullable = false)
    private Double llmStrongPassThreshold;

    @Column(name = "llm_cross_modal_threshold", nullable = false)
    private Double llmCrossModalThreshold;

    @Column(name = "report_human_threshold", nullable = false)
    private Integer reportHumanThreshold;

    @Column(name = "chunk_threshold_chars", nullable = false)
    private Integer chunkThresholdChars;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "thresholds_json")
    private Map<String, Object> thresholds;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
