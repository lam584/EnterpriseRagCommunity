package com.example.EnterpriseRagCommunity.entity.monitor;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "search_logs")
public class SearchLogsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "query", length = 512, nullable = false)
    private String query;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "results_count")
    private Integer resultsCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
