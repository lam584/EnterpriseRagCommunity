package com.example.EnterpriseRagCommunity.entity.monitor;

import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "rag_eval_runs")
public class RagEvalRunsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // name VARCHAR(96) NOT NULL
    @Column(name = "name", length = 96, nullable = false)
    private String name;

    // config JSON NULL -> Map<String,Object>
    @Convert(converter = MapJsonConverter.class)
    @Column(name = "config", columnDefinition = "json")
    private Map<String, Object> config;

    // is_baseline TINYINT(1) NOT NULL DEFAULT 0
    @Column(name = "is_baseline", nullable = false)
    private Boolean isBaseline;

    // created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
