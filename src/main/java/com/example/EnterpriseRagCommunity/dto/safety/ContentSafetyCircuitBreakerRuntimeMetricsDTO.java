package com.example.EnterpriseRagCommunity.dto.safety;

import lombok.Data;

import java.util.Map;

@Data
public class ContentSafetyCircuitBreakerRuntimeMetricsDTO {
    private Long blockedTotal;
    private Long blockedLast60s;
    private Map<String, Long> blockedByEntrypoint;
}

