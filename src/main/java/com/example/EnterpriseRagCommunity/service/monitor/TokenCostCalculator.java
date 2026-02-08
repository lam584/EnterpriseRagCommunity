package com.example.EnterpriseRagCommunity.service.monitor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class TokenCostCalculator {
    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private TokenCostCalculator() {
    }

    public static BigDecimal computeCost(BigDecimal inputCostPer1k, BigDecimal outputCostPer1k, long tokensIn, long tokensOut) {
        BigDecimal inPrice = inputCostPer1k;
        BigDecimal outPrice = outputCostPer1k == null ? inputCostPer1k : outputCostPer1k;
        BigDecimal cost = BigDecimal.ZERO;
        if (inPrice != null && tokensIn > 0) {
            cost = cost.add(inPrice.multiply(BigDecimal.valueOf(tokensIn)).divide(ONE_THOUSAND, 8, RoundingMode.HALF_UP));
        }
        if (outPrice != null && tokensOut > 0) {
            cost = cost.add(outPrice.multiply(BigDecimal.valueOf(tokensOut)).divide(ONE_THOUSAND, 8, RoundingMode.HALF_UP));
        }
        return cost;
    }

    public static BigDecimal computeCost(LlmPricing.Config pricing, LlmPricing.Mode mode, long tokensIn, long tokensOut) {
        if (pricing == null) return BigDecimal.ZERO;
        long in = Math.max(0, tokensIn);
        long out = Math.max(0, tokensOut);
        long totalTokens = in + out;
        if (totalTokens <= 0) return BigDecimal.ZERO;

        BigDecimal unitDiv = pricing.unit() == LlmPricing.Unit.PER_1M ? ONE_MILLION : ONE_THOUSAND;

        BigDecimal inputCostPerUnit;
        BigDecimal outputCostPerUnit;
        if (pricing.strategy() == LlmPricing.Strategy.TIERED) {
            LlmPricing.Tier tier = selectTier(pricing.tiers(), totalTokens);
            inputCostPerUnit = tier == null ? null : tier.inputCostPerUnit();
            outputCostPerUnit = tier == null ? null : tier.outputCostPerUnit();
        } else {
            inputCostPerUnit = LlmPricing.resolveInputCostPerUnit(pricing, mode);
            outputCostPerUnit = LlmPricing.resolveOutputCostPerUnit(pricing, mode);
        }

        BigDecimal inPrice = inputCostPerUnit;
        BigDecimal outPrice = outputCostPerUnit == null ? inputCostPerUnit : outputCostPerUnit;
        BigDecimal cost = BigDecimal.ZERO;
        if (inPrice != null && in > 0) {
            cost = cost.add(inPrice.multiply(BigDecimal.valueOf(in)).divide(unitDiv, 8, RoundingMode.HALF_UP));
        }
        if (outPrice != null && out > 0) {
            cost = cost.add(outPrice.multiply(BigDecimal.valueOf(out)).divide(unitDiv, 8, RoundingMode.HALF_UP));
        }
        return cost;
    }

    private static LlmPricing.Tier selectTier(List<LlmPricing.Tier> tiers, long totalTokens) {
        if (tiers == null || tiers.isEmpty()) return null;
        LlmPricing.Tier last = null;
        for (LlmPricing.Tier t : tiers) {
            if (t == null) continue;
            last = t;
            if (totalTokens <= t.upToTokens()) return t;
        }
        return last;
    }
}
