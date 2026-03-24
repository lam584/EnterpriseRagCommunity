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
