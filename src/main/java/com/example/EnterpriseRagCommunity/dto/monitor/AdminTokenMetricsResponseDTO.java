package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminTokenMetricsResponseDTO {
    private LocalDateTime start;
    private LocalDateTime end;
    private String currency;
    private Long totalTokens;
    private BigDecimal totalCost;
    private List<AdminTokenMetricsModelItemDTO> items;
}

