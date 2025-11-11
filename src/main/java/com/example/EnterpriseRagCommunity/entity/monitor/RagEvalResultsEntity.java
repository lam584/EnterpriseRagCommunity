package com.example.EnterpriseRagCommunity.entity.monitor;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "rag_eval_results")
public class RagEvalResultsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // run_id BIGINT UNSIGNED NOT NULL
    @Column(name = "run_id", nullable = false)
    private Long runId;

    // sample_id BIGINT UNSIGNED NOT NULL
    @Column(name = "sample_id", nullable = false)
    private Long sampleId;

    // em DOUBLE NULL
    @Column(name = "em")
    private Double em;

    // f1 DOUBLE NULL
    @Column(name = "f1")
    private Double f1;

    // hit_rate DOUBLE NULL
    @Column(name = "hit_rate")
    private Double hitRate;

    // latency_ms INT NULL
    @Column(name = "latency_ms")
    private Integer latencyMs;

    // tokens_in INT NULL
    @Column(name = "tokens_in")
    private Integer tokensIn;

    // tokens_out INT NULL
    @Column(name = "tokens_out")
    private Integer tokensOut;

    // cost_cents INT NULL
    @Column(name = "cost_cents")
    private Integer costCents;

    // created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
