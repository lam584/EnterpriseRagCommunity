package com.example.EnterpriseRagCommunity.entity.ai;

import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "llm_models")
public class LlmModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "env", length = 32, nullable = false)
    private String env;

    @Column(name = "provider_id", length = 64, nullable = false)
    private String providerId;

    @Column(name = "purpose", length = 64, nullable = false)
    private String purpose;

    @Column(name = "model_name", length = 128, nullable = false)
    private String modelName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "weight", nullable = false)
    private Integer weight;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "sort_index", nullable = false)
    private Integer sortIndex;

    @Column(name = "max_concurrent")
    private Integer maxConcurrent;

    @Column(name = "min_delay_ms")
    private Integer minDelayMs;

    @Column(name = "qps")
    private Double qps;

    @Column(name = "price_config_id")
    private Long priceConfigId;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
