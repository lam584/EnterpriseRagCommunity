package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class AdminLlmRoutingPolicyDTO {
    private String taskType;
    private String strategy;
    private Integer maxAttempts;
    private Integer failureThreshold;
    private Integer cooldownMs;
}
