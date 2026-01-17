package com.example.EnterpriseRagCommunity.entity.moderation;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
