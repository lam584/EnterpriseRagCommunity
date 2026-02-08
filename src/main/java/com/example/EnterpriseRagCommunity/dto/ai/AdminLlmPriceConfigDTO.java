package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminLlmPriceConfigDTO {
    private Long id;
    private String name;
    private String currency;
    private BigDecimal inputCostPer1k;
    private BigDecimal outputCostPer1k;
    private AdminLlmPriceConfigPricingDTO pricing;
    private LocalDateTime updatedAt;
}
