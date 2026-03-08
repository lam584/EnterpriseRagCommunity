package com.example.EnterpriseRagCommunity.dto.safety;

import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import lombok.Data;

import java.util.Map;

@Data
public class AdminCircuitBreakerMetricsDTO {
    private ContentSafetyCircuitBreakerStatusDTO contentSafety;
    private Map<String, DependencyCircuitBreakerService.Snapshot> dependencies;
}

