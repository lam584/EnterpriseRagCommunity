package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.util.List;

@Data
public class AdminLlmRoutingDecisionResponseDTO {
    private Long checkedAtMs;
    private List<AdminLlmRoutingDecisionEventDTO> items;
}

