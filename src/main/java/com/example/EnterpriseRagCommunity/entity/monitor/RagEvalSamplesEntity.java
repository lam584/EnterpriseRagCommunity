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
@Table(name = "rag_eval_samples")
public class RagEvalSamplesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // run_id BIGINT UNSIGNED NOT NULL (foreign key to rag_eval_runs.id) - store as scalar per naming convention
    @Column(name = "run_id", nullable = false)
    private Long runId;

    // query TEXT NOT NULL
    @Column(name = "query", columnDefinition = "text", nullable = false)
    private String query;

    // expected_answer TEXT NULL
    @Column(name = "expected_answer", columnDefinition = "text")
    private String expectedAnswer;

    // references_json JSON NULL
    @Convert(converter = MapJsonConverter.class)
    @Column(name = "references_json", columnDefinition = "json")
    private Map<String, Object> referencesJson;

    // created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

// Duplicate placeholder. Use classes from package com.example.EnterpriseRagCommunity.entity.monitor.
