package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.util.List;

@Data
public class AdminLlmRoutingStateResponseDTO {
    private Long checkedAtMs;
    private String taskType;

    private String strategy;
    private Integer maxAttempts;
    private Integer failureThreshold;
    private Integer cooldownMs;

    private List<AdminLlmRoutingStateItemDTO> items;
}

