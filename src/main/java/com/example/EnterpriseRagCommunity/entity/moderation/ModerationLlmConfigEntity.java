package com.example.EnterpriseRagCommunity.entity.moderation;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_llm_config")
public class ModerationLlmConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Lob
    @Column(name = "prompt_template", nullable = false)
    private String promptTemplate;

    @Lob
    @Column(name = "vision_prompt_template")
    private String visionPromptTemplate;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "vision_model", length = 128)
    private String visionModel;

    @Column(name = "vision_provider_id", length = 64)
    private String visionProviderId;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "top_p")
    private Double topP;

    @Column(name = "vision_temperature")
    private Double visionTemperature;

    @Column(name = "vision_top_p")
    private Double visionTopP;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "enable_thinking", nullable = false)
    private Boolean enableThinking;

    @Column(name = "vision_max_tokens")
    private Integer visionMaxTokens;

    @Column(name = "vision_enable_thinking", nullable = false)
    private Boolean visionEnableThinking;

    @Column(name = "threshold")
    private Double threshold;

    @Column(name = "auto_run", nullable = false)
    private Boolean autoRun;

    @Column(name = "max_concurrent")
    private Integer maxConcurrent;

    @Column(name = "min_delay_ms")
    private Integer minDelayMs;

    @Column(name = "qps")
    private Double qps;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
