package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_pipeline_run")
public class ModerationPipelineRunEntity {

    public enum RunStatus { RUNNING, SUCCESS, FAIL }

    public enum FinalDecision { APPROVE, REJECT, HUMAN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", length = 16)
    private FinalDecision finalDecision;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "total_ms")
    private Long totalMs;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "llm_model", length = 128)
    private String llmModel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
