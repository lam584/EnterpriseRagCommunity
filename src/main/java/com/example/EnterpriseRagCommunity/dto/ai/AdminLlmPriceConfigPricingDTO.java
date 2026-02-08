package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AdminLlmPriceConfigPricingDTO {
    private String strategy;
    private String unit;

    private BigDecimal defaultInputCostPerUnit;
    private BigDecimal defaultOutputCostPerUnit;

    private BigDecimal nonThinkingInputCostPerUnit;
    private BigDecimal nonThinkingOutputCostPerUnit;

    private BigDecimal thinkingInputCostPerUnit;
    private BigDecimal thinkingOutputCostPerUnit;

    private List<AdminLlmPriceConfigPricingTierDTO> tiers;
}

