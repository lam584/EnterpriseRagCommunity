package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "llm_routing_scenarios")
public class LlmRoutingScenarioEntity {

    @Id
    @Column(name = "task_type", length = 64, nullable = false)
    private String taskType;

    @Column(name = "label", length = 128, nullable = false)
    private String label;

    @Column(name = "category", length = 32, nullable = false)
    private String category;

    @Column(name = "sort_index", nullable = false)
    private Integer sortIndex;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
