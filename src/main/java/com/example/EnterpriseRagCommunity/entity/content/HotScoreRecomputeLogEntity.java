package com.example.EnterpriseRagCommunity.entity.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "hot_score_recompute_logs")
public class HotScoreRecomputeLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "window_type", nullable = false, length = 32)
    private String windowType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "changed_count", nullable = false)
    private Integer changedCount;

    @Column(name = "increased_count", nullable = false)
    private Integer increasedCount;

    @Column(name = "decreased_count", nullable = false)
    private Integer decreasedCount;

    @Column(name = "unchanged_count", nullable = false)
    private Integer unchangedCount;

    @Column(name = "increased_score_delta", nullable = false)
    private Double increasedScoreDelta;

    @Column(name = "decreased_score_delta", nullable = false)
    private Double decreasedScoreDelta;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
