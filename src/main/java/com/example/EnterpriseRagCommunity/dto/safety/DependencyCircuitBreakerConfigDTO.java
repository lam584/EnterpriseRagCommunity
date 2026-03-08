package com.example.EnterpriseRagCommunity.dto.safety;

import lombok.Data;

@Data
public class DependencyCircuitBreakerConfigDTO {
    private String dependency;
    private Integer failureThreshold;
    private Integer cooldownSeconds;
}

