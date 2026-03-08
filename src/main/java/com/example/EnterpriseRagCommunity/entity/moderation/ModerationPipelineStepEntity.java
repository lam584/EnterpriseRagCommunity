package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_pipeline_step")
public class ModerationPipelineStepEntity {

    public enum Stage { RULE, VEC, TEXT, VISION, JUDGE, UPGRADE, LLM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 16)
    private Stage stage;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "decision", length = 32)
    private String decision;

    @Column(name = "score", precision = 10, scale = 6)
    private BigDecimal score;

    @Column(name = "threshold", precision = 10, scale = 6)
    private BigDecimal threshold;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "details_json", columnDefinition = "json")
    private Map<String, Object> detailsJson;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "cost_ms")
    private Long costMs;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;
}
