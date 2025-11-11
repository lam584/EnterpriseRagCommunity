package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_llm_decisions")
public class ModerationLlmDecisionsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter.class)
    @Column(name = "labels", nullable = false, columnDefinition = "json")
    private Map<String, Object> labels;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private java.math.BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false, length = 16)
    private Verdict verdict;

    // Use Long promptId to align with SQL foreign key column; keep optional read-only relation for convenience
    @Column(name = "prompt_id", nullable = true)
    private Long promptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", insertable = false, updatable = false)
    private com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity promptRef;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;
}
