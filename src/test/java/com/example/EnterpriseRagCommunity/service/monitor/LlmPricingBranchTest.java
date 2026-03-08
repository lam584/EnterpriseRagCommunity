package com.example.EnterpriseRagCommunity.service.monitor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmPricingBranchTest {

    @Test
    void fromMetadata_shouldReturnNull_whenMetadataNullOrEmpty() {
        assertNull(LlmPricing.fromMetadata(null));
        assertNull(LlmPricing.fromMetadata(Map.of()));
    }

    @Test
    void fromMetadata_shouldReturnNull_whenPricingNotMap() {
        assertNull(LlmPricing.fromMetadata(Map.of("pricing", "x")));
    }

    @Test
    void fromMetadata_shouldDefaultStrategyAndUnit_andFilterSortTiers() {
        Map<String, Object> pricing = new HashMap<>();
        pricing.put("strategy", "  ");
        pricing.put("unit", "bad");

        List<Object> tiers = new ArrayList<>();
        tiers.add("x");

        Map<String, Object> invalidUpTo = new HashMap<>();
        invalidUpTo.put("upToTokens", 0);
        tiers.add(invalidUpTo);

        Map<String, Object> tier2 = new HashMap<>();
        tier2.put("upToTokens", 20);
        tier2.put("inputCostPerUnit", "0.2");
        tiers.add(tier2);

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("upToTokens", "10");
        tier1.put("inputCostPerUnit", new BigDecimal("0.1"));
        tiers.add(tier1);

        pricing.put("tiers", tiers);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(Map.of("pricing", pricing));
        assertNotNull(cfg);
        assertEquals(LlmPricing.Strategy.FLAT, cfg.strategy());
        assertEquals(LlmPricing.Unit.PER_1K, cfg.unit());
        assertNotNull(cfg.tiers());
        assertEquals(2, cfg.tiers().size());
        assertEquals(10, cfg.tiers().get(0).upToTokens());
        assertEquals(20, cfg.tiers().get(1).upToTokens());
    }

    @Test
    void fromMetadata_shouldReturnNull_whenAllCostsNull_andNoValidTiers() {
        Map<String, Object> pricing = new HashMap<>();
        pricing.put("strategy", "flat");
        pricing.put("unit", "per_1k");
        pricing.put("defaultInputCostPerUnit", " ");
        pricing.put("defaultOutputCostPerUnit", " ");
        pricing.put("nonThinkingInputCostPerUnit", " ");
        pricing.put("nonThinkingOutputCostPerUnit", " ");
        pricing.put("thinkingInputCostPerUnit", " ");
        pricing.put("thinkingOutputCostPerUnit", " ");

        List<Object> tiers = new ArrayList<>();
        Map<String, Object> invalidTier = new HashMap<>();
        invalidTier.put("upToTokens", 0);
        tiers.add(invalidTier);
        pricing.put("tiers", tiers);

        assertNull(LlmPricing.fromMetadata(Map.of("pricing", pricing)));
    }

    @Test
    void isConfiguredForMode_and_resolve_shouldRespectPrecedenceAndFallback() {
        LlmPricing.Config tiered = new LlmPricing.Config(
                LlmPricing.Strategy.TIERED,
                LlmPricing.Unit.PER_1K,
                null,
                null,
                null,
                null,
                null,
                null,
                Arrays.asList(
                        null,
                        new LlmPricing.Tier(100, null, new BigDecimal("0.01"))
                )
        );
        assertTrue(LlmPricing.isConfiguredForMode(tiered, LlmPricing.Mode.DEFAULT));

        LlmPricing.Config flat = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                new BigDecimal("1"),
                new BigDecimal("10"),
                new BigDecimal("2"),
                null,
                null,
                new BigDecimal("30"),
                null
        );
        assertEquals(new BigDecimal("2"), LlmPricing.resolveInputCostPerUnit(flat, LlmPricing.Mode.NON_THINKING));
        assertEquals(new BigDecimal("1"), LlmPricing.resolveInputCostPerUnit(flat, LlmPricing.Mode.THINKING));
        assertEquals(new BigDecimal("1"), LlmPricing.resolveInputCostPerUnit(flat, null));

        assertEquals(new BigDecimal("10"), LlmPricing.resolveOutputCostPerUnit(flat, LlmPricing.Mode.NON_THINKING));
        assertEquals(new BigDecimal("30"), LlmPricing.resolveOutputCostPerUnit(flat, LlmPricing.Mode.THINKING));

        assertTrue(LlmPricing.isConfiguredForMode(flat, LlmPricing.Mode.THINKING));
    }
}
