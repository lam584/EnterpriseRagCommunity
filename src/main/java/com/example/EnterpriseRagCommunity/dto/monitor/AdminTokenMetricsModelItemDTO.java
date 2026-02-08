package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminTokenMetricsModelItemDTO {
    private String model;
    private Long tokensIn;
    private Long tokensOut;
    private Long totalTokens;
    private BigDecimal cost;
    private Boolean priceMissing;
}

