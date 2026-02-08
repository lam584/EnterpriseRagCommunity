package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_ai_summary")
public class PostAiSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "summary_title", length = 512)
    private String summaryTitle;

    @Lob
    @Column(name = "summary_text")
    private String summaryText;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "applied_max_content_chars")
    private Integer appliedMaxContentChars;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
