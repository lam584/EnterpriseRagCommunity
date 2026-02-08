package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

@Data
public class AdminLlmLoadBalancePointDTO {
    private Long tsMs;
    private Long count;
    private Long errorCount;
    private Long throttled429Count;
    private Double qps;
    private Double avgResponseMs;
    private Double errorRate;
    private Double throttled429Rate;
    private Double p95ResponseMs;
}
