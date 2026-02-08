package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminLlmPriceConfigUpsertRequest {
    @NotBlank
    private String name;
    private String currency;
    private BigDecimal inputCostPer1k;
    private BigDecimal outputCostPer1k;
    private AdminLlmPriceConfigPricingDTO pricing;
}
