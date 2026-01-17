package com.example.EnterpriseRagCommunity.entity.moderation;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_samples")
public class ModerationSamplesEntity {

    public enum Category {
        AD_SAMPLE,
        HISTORY_VIOLATION
    }

    public enum ContentType {
        POST,
        COMMENT
    }

    public enum Source {
        HUMAN,
        RULE,
        LLM,
        IMPORT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_content_type", length = 16)
    private ContentType refContentType;

    @Column(name = "ref_content_id")
    private Long refContentId;

    @Lob
    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Lob
    @Column(name = "normalized_text", nullable = false)
    private String normalizedText;

    @Column(name = "text_hash", nullable = false, length = 64)
    private String textHash;

    @Column(name = "risk_level", nullable = false)
    private Integer riskLevel;

    /**
     * Stored as JSON in MySQL; keep as String to avoid adding JSON mapping dependencies.
     */
    @Lob
    @Column(name = "labels")
    private String labels;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private Source source;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
