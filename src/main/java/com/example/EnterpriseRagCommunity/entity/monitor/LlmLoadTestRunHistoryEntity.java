package com.example.EnterpriseRagCommunity.entity.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "llm_loadtest_run_history")
public class LlmLoadTestRunHistoryEntity {

    @Id
    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "stream")
    private Boolean stream;

    @Column(name = "enable_thinking")
    private Boolean enableThinking;

    @Column(name = "retries")
    private Integer retries;

    @Column(name = "retry_delay_ms")
    private Integer retryDelayMs;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Lob
    @Column(name = "summary_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String summaryJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
