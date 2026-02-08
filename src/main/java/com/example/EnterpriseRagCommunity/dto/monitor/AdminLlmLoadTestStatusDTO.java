package com.example.EnterpriseRagCommunity.dto.monitor;

import java.util.List;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class AdminLlmLoadTestStatusDTO {
    private String runId;
    private Long createdAtMs;
    private Long startedAtMs;
    private Long finishedAtMs;
    private Boolean running;
    private Boolean cancelled;
    private String error;
    private Integer done;
    private Integer total;
    private Integer success;
    private Integer failed;
    private Double avgLatencyMs;
    private Long maxLatencyMs;
    private Double p50LatencyMs;
    private Double p95LatencyMs;
    private Long tokensTotal;
    private Long tokensInTotal;
    private Long tokensOutTotal;
    private BigDecimal totalCost;
    private String currency;
    private Boolean priceMissing;
    private AdminLlmLoadTestQueuePeakDTO queuePeak;
    private List<AdminLlmLoadTestResultDTO> recentResults;
}
