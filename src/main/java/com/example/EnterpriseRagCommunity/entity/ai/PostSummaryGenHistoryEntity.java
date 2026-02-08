package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_summary_gen_history")
public class PostSummaryGenHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "applied_max_content_chars", nullable = false)
    private Integer appliedMaxContentChars;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "prompt_version")
    private Integer promptVersion;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;
}
