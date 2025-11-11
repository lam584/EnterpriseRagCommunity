package com.example.EnterpriseRagCommunity.entity.monitor;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "review_efficiency")
public class ReviewEfficiencyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;

    @Column(name = "total", nullable = false)
    private Integer total;

    @Column(name = "human_share", nullable = false, precision = 5, scale = 4)
    private BigDecimal humanShare;

    @Column(name = "avg_latency_ms", nullable = false)
    private Integer avgLatencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
