package com.example.EnterpriseRagCommunity.entity.monitor;

import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskStatus;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "llm_queue_task_history")
public class LlmQueueTaskHistoryEntity {

    @Id
    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;

    @Column(name = "seq", nullable = false)
    private Long seq;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 64, nullable = false)
    private LlmQueueTaskType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private LlmQueueTaskStatus status;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "wait_ms")
    private Long waitMs;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "tokens_per_sec")
    private Double tokensPerSec;

    @Column(name = "error", length = 1024)
    private String error;

    @Lob
    @Column(name = "input", columnDefinition = "MEDIUMTEXT")
    private String input;

    @Lob
    @Column(name = "output", columnDefinition = "MEDIUMTEXT")
    private String output;

    @Column(name = "input_chars")
    private Integer inputChars;

    @Column(name = "output_chars")
    private Integer outputChars;

    @Column(name = "input_truncated", nullable = false)
    private Boolean inputTruncated;

    @Column(name = "output_truncated", nullable = false)
    private Boolean outputTruncated;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
