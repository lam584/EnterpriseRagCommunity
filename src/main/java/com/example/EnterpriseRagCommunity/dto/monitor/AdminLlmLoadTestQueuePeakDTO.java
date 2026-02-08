package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

@Data
public class AdminLlmLoadTestQueuePeakDTO {
    private Integer maxPending;
    private Integer maxRunning;
    private Integer maxTotal;
    private Double tokensPerSecMax;
    private Double tokensPerSecAvg;
}
