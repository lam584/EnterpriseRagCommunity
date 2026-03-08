package com.example.EnterpriseRagCommunity.dto.safety;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class ContentSafetyCircuitBreakerStatusDTO {
    private ContentSafetyCircuitBreakerConfigDTO config;
    private Instant updatedAt;
    private String updatedBy;
    private Long updatedByUserId;
    private Boolean persisted;
    private Instant lastPersistAt;
    private ContentSafetyCircuitBreakerRuntimeMetricsDTO runtimeMetrics;
    private List<ContentSafetyCircuitBreakerEventDTO> recentEvents;
}
