package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.util.List;

@Data
public class AdminLlmLoadBalanceModelDTO {
    private String providerId;
    private String modelName;
    private Long count;
    private Double qps;
    private Double avgResponseMs;
    private Double errorRate;
    private Double throttled429Rate;
    private Double p95ResponseMs;
    private List<AdminLlmLoadBalancePointDTO> points;
}
