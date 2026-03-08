package com.example.EnterpriseRagCommunity.service.monitor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TokenCostCalculatorTest {

    @Test
    void computeCost_supportsUnifiedPricing() {
        BigDecimal cost = TokenCostCalculator.computeCost(new BigDecimal("0.12"), null, 500, 1500);
        assertEquals(new BigDecimal("0.24000000"), cost);
    }

    @Test
    void computeCost_supportsSplitPricing() {
        BigDecimal cost = TokenCostCalculator.computeCost(new BigDecimal("0.10"), new BigDecimal("0.20"), 1000, 2000);
        assertEquals(new BigDecimal("0.50000000"), cost);
    }

    @Test
    void computeCost_handlesNullPrices() {
        BigDecimal cost = TokenCostCalculator.computeCost((BigDecimal) null, (BigDecimal) null, 1000, 1000);
        assertEquals(new BigDecimal("0"), cost);
    }

    @Test
    void computeCost_shouldSkipInput_whenInputPriceNull() {
        BigDecimal cost = TokenCostCalculator.computeCost((BigDecimal) null, new BigDecimal("0.03"), 1000, 1000);
        assertEquals(new BigDecimal("0.03000000"), cost);
    }

    @Test
    void computeCost_shouldSkipOutput_whenTokensOutNonPositive() {
        BigDecimal cost = TokenCostCalculator.computeCost(new BigDecimal("0.02"), new BigDecimal("0.03"), 1000, 0);
        assertEquals(new BigDecimal("0.02000000"), cost);
    }

    @Test
    void computeCost_shouldSkipInput_whenTokensInNonPositive() {
        BigDecimal cost = TokenCostCalculator.computeCost(new BigDecimal("0.02"), new BigDecimal("0.03"), 0, 1000);
        assertEquals(new BigDecimal("0.03000000"), cost);
    }

    @Test
    void computeCost_shouldReturnZero_whenPricingNull() {
        BigDecimal cost = TokenCostCalculator.computeCost((LlmPricing.Config) null, null, 1000, 1000);
        assertEquals(BigDecimal.ZERO, cost);
    }

    @Test
    void computeCost_shouldClampNegativeTokensAndReturnZero_whenTotalNonPositive() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                new BigDecimal("0.02"),
                null,
                null,
                null,
                null,
                null,
                null
        );
        BigDecimal cost = TokenCostCalculator.computeCost(cfg, LlmPricing.Mode.DEFAULT, -1, -2);
        assertEquals(BigDecimal.ZERO, cost);
    }

    @Test
    void computeCost_shouldFallbackOutputToInput_whenFlatOutputPriceNull() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                new BigDecimal("0.02"),
                null,
                null,
                null,
                null,
                null,
                null
        );
        BigDecimal cost = TokenCostCalculator.computeCost(cfg, LlmPricing.Mode.DEFAULT, -1, 1000);
        assertEquals(new BigDecimal("0.02000000"), cost);
    }

    @Test
    void computeCost_shouldUsePer1mDivisor_whenUnitPer1m() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1M,
                new BigDecimal("1.00"),
                new BigDecimal("2.00"),
                null,
                null,
                null,
                null,
                null
        );
        BigDecimal cost = TokenCostCalculator.computeCost(cfg, LlmPricing.Mode.DEFAULT, 1_000_000, 0);
        assertEquals(new BigDecimal("1.00000000"), cost);
    }

    @Test
    void computeCost_shouldReturnZero_whenTieredAndTiersNull() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.TIERED,
                LlmPricing.Unit.PER_1K,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        BigDecimal cost = TokenCostCalculator.computeCost(cfg, LlmPricing.Mode.DEFAULT, 1000, 1000);
        assertEquals(BigDecimal.ZERO, cost);
    }

    @Test
    void computeCost_shouldReturnZero_whenTieredAndTiersEmpty() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.TIERED,
                LlmPricing.Unit.PER_1K,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
        BigDecimal cost = TokenCostCalculator.computeCost(cfg, LlmPricing.Mode.DEFAULT, 1000, 1000);
        assertEquals(BigDecimal.ZERO, cost);
    }

    @Test
    void computeCost_shouldUseTierBoundaryAndFallbackOutput_whenTierOutputNull() {
        List<LlmPricing.Tier> tiers = new ArrayList<>();
        tiers.add(null);
        tiers.add(new LlmPricing.Tier(10, new BigDecimal("0.02"), null));
        tiers.add(new LlmPricing.Tier(20, new BigDecimal("0.03"), new BigDecimal("0.04")));

        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.TIERED,
                LlmPricing.Unit.PER_1K,
                null,
                null,
                null,
                null,
                null,
                null,
                tiers
        );
        BigDecimal cost = TokenCostCalculator.computeCost(cfg, LlmPricing.Mode.DEFAULT, 5, 5);
        assertEquals(new BigDecimal("0.00020000"), cost);
    }

    @Test
    void computeCost_shouldReturnLastTier_whenOverMaxUpToTokens() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.TIERED,
                LlmPricing.Unit.PER_1K,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new LlmPricing.Tier(10, new BigDecimal("0.02"), new BigDecimal("0.03")),
                        new LlmPricing.Tier(20, new BigDecimal("0.04"), new BigDecimal("0.05"))
                )
        );
        BigDecimal cost = TokenCostCalculator.computeCost(cfg, LlmPricing.Mode.DEFAULT, 10, 15);
        assertEquals(new BigDecimal("0.00115000"), cost);
    }
}
