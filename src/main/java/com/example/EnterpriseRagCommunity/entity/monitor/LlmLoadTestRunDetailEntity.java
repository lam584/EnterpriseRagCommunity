package com.example.EnterpriseRagCommunity.entity.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "llm_loadtest_run_detail")
public class LlmLoadTestRunDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "req_index", nullable = false)
    private Integer reqIndex;

    @Column(name = "kind", length = 32, nullable = false)
    private String kind;

    @Column(name = "ok", nullable = false)
    private Boolean ok;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "error", length = 1024)
    private String error;

    @Lob
    @Column(name = "request_json", columnDefinition = "MEDIUMTEXT")
    private String requestJson;

    @Lob
    @Column(name = "response_json", columnDefinition = "MEDIUMTEXT")
    private String responseJson;

    @Column(name = "request_chars")
    private Integer requestChars;

    @Column(name = "response_chars")
    private Integer responseChars;

    @Column(name = "request_truncated", nullable = false)
    private Boolean requestTruncated;

    @Column(name = "response_truncated", nullable = false)
    private Boolean responseTruncated;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

