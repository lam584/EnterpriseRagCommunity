package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "llm_routing_policies")
public class LlmRoutingPolicyEntity {

    @EmbeddedId
    private LlmRoutingPolicyId id;

    @Column(name = "strategy", length = 32, nullable = false)
    private String strategy;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "failure_threshold", nullable = false)
    private Integer failureThreshold;

    @Column(name = "cooldown_ms", nullable = false)
    private Integer cooldownMs;

    @Column(name = "probe_enabled", nullable = false)
    private Boolean probeEnabled;

    @Column(name = "probe_interval_ms")
    private Integer probeIntervalMs;

    @Column(name = "probe_path", length = 128)
    private String probePath;

    @Column(name = "label", length = 128)
    private String label;

    @Column(name = "category", length = 32)
    private String category;

    @Column(name = "sort_index", nullable = false)
    private Integer sortIndex;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
