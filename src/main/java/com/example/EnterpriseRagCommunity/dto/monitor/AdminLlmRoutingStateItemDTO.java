package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

@Data
public class AdminLlmRoutingStateItemDTO {
    private String taskType;
    private String providerId;
    private String modelName;

    private Integer weight;
    private Integer priority;
    private Double qps;

    private Integer runningCount;

    private Integer consecutiveFailures;
    private Long cooldownUntilMs;
    private Long cooldownRemainingMs;

    private Integer currentWeight;

    private Long lastDispatchAtMs;
    private Double rateTokens;
    private Long lastRefillAtMs;
}

