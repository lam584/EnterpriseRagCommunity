package com.example.EnterpriseRagCommunity.entity.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobType;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationTargetType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "generation_jobs")
public class GenerationJobsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 16)
    private GenerationJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private GenerationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private GenerationJobStatus status;

    @Column(name = "prompt_id")
    private Long promptId;

    @Column(name = "model", length = 64)
    private String model;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.JsonConverter.class)
    @Column(name = "params", columnDefinition = "json")
    private Map<String, Object> params;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.JsonConverter.class)
    @Column(name = "result_json", columnDefinition = "json")
    private Map<String, Object> resultJson;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "cost_cents")
    private Integer costCents;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
