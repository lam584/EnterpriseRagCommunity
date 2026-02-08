package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminLlmPriceConfigPricingTierDTO {
    private Long upToTokens;
    private BigDecimal inputCostPerUnit;
    private BigDecimal outputCostPerUnit;
}

