package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "semantic_translate_history")
public class SemanticTranslateHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "source_type", length = 16, nullable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "target_lang", length = 32, nullable = false)
    private String targetLang;

    @Column(name = "source_hash", length = 64, nullable = false)
    private String sourceHash;

    @Column(name = "config_hash", length = 64, nullable = false)
    private String configHash;

    @Column(name = "source_title_excerpt", length = 160)
    private String sourceTitleExcerpt;

    @Column(name = "source_content_excerpt", length = 512)
    private String sourceContentExcerpt;

    @Column(name = "translated_title", length = 512)
    private String translatedTitle;

    @Lob
    @Column(name = "translated_markdown", nullable = false)
    private String translatedMarkdown;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "prompt_version")
    private Integer promptVersion;
}
