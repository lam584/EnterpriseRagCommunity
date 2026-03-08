package com.example.EnterpriseRagCommunity.service.monitor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LlmPricingTest {

    @Test
    void modeFromNullableString_shouldHandleNullBlankValidAndInvalid() {
        assertNull(LlmPricing.Mode.fromNullableString(null));
        assertNull(LlmPricing.Mode.fromNullableString("   "));
        assertEquals(LlmPricing.Mode.DEFAULT, LlmPricing.Mode.fromNullableString("default"));
        assertEquals(LlmPricing.Mode.NON_THINKING, LlmPricing.Mode.fromNullableString("Non_Thinking"));
        assertNull(LlmPricing.Mode.fromNullableString("not_a_mode"));
    }

    @Test
    void unitFromNullableString_shouldHandleNullBlankValidAndInvalid() {
        assertNull(LlmPricing.Unit.fromNullableString(null));
        assertNull(LlmPricing.Unit.fromNullableString("   "));
        assertEquals(LlmPricing.Unit.PER_1K, LlmPricing.Unit.fromNullableString("per_1k"));
        assertEquals(LlmPricing.Unit.PER_1M, LlmPricing.Unit.fromNullableString("PER_1M"));
        assertNull(LlmPricing.Unit.fromNullableString("not_a_unit"));
    }

    @Test
    void strategyFromNullableString_shouldHandleNullBlankValidAndInvalid() {
        assertNull(LlmPricing.Strategy.fromNullableString(null));
        assertNull(LlmPricing.Strategy.fromNullableString("   "));
        assertEquals(LlmPricing.Strategy.FLAT, LlmPricing.Strategy.fromNullableString("flat"));
        assertEquals(LlmPricing.Strategy.TIERED, LlmPricing.Strategy.fromNullableString("TIERED"));
        assertNull(LlmPricing.Strategy.fromNullableString("not_a_strategy"));
    }

    @Test
    void fromLegacy_shouldReturnNull_whenBothPricesNull() {
        assertNull(LlmPricing.fromLegacy(null, null));
    }

    @Test
    void fromLegacy_shouldBuildFlatPer1kConfig_whenAnyPricePresent() {
        BigDecimal input = new BigDecimal("0.01");
        BigDecimal output = new BigDecimal("0.02");

        LlmPricing.Config cfg = LlmPricing.fromLegacy(input, output);
        assertNotNull(cfg);
        assertEquals(LlmPricing.Strategy.FLAT, cfg.strategy());
        assertEquals(LlmPricing.Unit.PER_1K, cfg.unit());
        assertEquals(input, cfg.defaultInputCostPerUnit());
        assertEquals(output, cfg.defaultOutputCostPerUnit());
        assertNull(cfg.nonThinkingInputCostPerUnit());
        assertNull(cfg.thinkingOutputCostPerUnit());
        assertNull(cfg.tiers());
    }

    @Test
    void fromLegacy_shouldBuildConfig_whenOnlyOneSidePresent() {
        LlmPricing.Config cfg = LlmPricing.fromLegacy(null, new BigDecimal("0.02"));
        assertNotNull(cfg);
        assertNull(cfg.defaultInputCostPerUnit());
        assertEquals(new BigDecimal("0.02"), cfg.defaultOutputCostPerUnit());
    }

    @Test
    void fromMetadata_shouldReturnNull_whenMetadataNullEmptyOrPricingNotMap() {
        assertNull(LlmPricing.fromMetadata(null));
        assertNull(LlmPricing.fromMetadata(Map.of()));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", "x");
        assertNull(LlmPricing.fromMetadata(m));
    }

    @Test
    void fromMetadata_shouldReturnNull_whenAllCostsNullAndTiersMissingOrEmptyAfterFilter() {
        Map<String, Object> p = new HashMap<>();
        p.put("strategy", "flat");
        p.put("unit", "per_1k");

        List<Object> tiers = new ArrayList<>();
        tiers.add("not-a-map");
        Map<String, Object> badUpTo = new HashMap<>();
        badUpTo.put("upToTokens", "0");
        tiers.add(badUpTo);
        p.put("tiers", tiers);

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        assertNull(LlmPricing.fromMetadata(m));
    }

    @Test
    void fromMetadata_shouldReturnConfig_whenDefaultOutOnly() {
        Map<String, Object> p = new HashMap<>();
        p.put("defaultOutputCostPerUnit", new BigDecimal("0.01"));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(new BigDecimal("0.01"), cfg.defaultOutputCostPerUnit());
    }

    @Test
    void fromMetadata_shouldReturnConfig_whenNonThinkingInOnly() {
        Map<String, Object> p = new HashMap<>();
        p.put("nonThinkingInputCostPerUnit", new BigDecimal("0.01"));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(new BigDecimal("0.01"), cfg.nonThinkingInputCostPerUnit());
    }

    @Test
    void fromMetadata_shouldReturnConfig_whenNonThinkingOutOnly() {
        Map<String, Object> p = new HashMap<>();
        p.put("nonThinkingOutputCostPerUnit", new BigDecimal("0.01"));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(new BigDecimal("0.01"), cfg.nonThinkingOutputCostPerUnit());
    }

    @Test
    void fromMetadata_shouldReturnConfig_whenThinkingInOnly() {
        Map<String, Object> p = new HashMap<>();
        p.put("thinkingInputCostPerUnit", new BigDecimal("0.01"));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(new BigDecimal("0.01"), cfg.thinkingInputCostPerUnit());
    }

    @Test
    void fromMetadata_shouldReturnConfig_whenThinkingOutOnly() {
        Map<String, Object> p = new HashMap<>();
        p.put("thinkingOutputCostPerUnit", new BigDecimal("0.01"));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(new BigDecimal("0.01"), cfg.thinkingOutputCostPerUnit());
    }

    @Test
    void fromMetadata_shouldSkipTierParsing_whenTiersListEmpty() {
        Map<String, Object> p = new HashMap<>();
        p.put("defaultInputCostPerUnit", new BigDecimal("0.01"));
        p.put("tiers", List.of());

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertNull(cfg.tiers());
    }

    @Test
    void fromMetadata_shouldFallbackStrategyAndUnit_whenMissingKeys() {
        Map<String, Object> p = new HashMap<>();
        p.put("defaultInputCostPerUnit", new BigDecimal("0.01"));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(LlmPricing.Strategy.FLAT, cfg.strategy());
        assertEquals(LlmPricing.Unit.PER_1K, cfg.unit());
    }

    @Test
    void fromMetadata_shouldFallbackStrategyAndUnit_whenMissingBlankOrInvalid() {
        Map<String, Object> p = new HashMap<>();
        p.put("strategy", "   ");
        p.put("unit", new Object());
        p.put("defaultInputCostPerUnit", new BigDecimal("0.01"));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(LlmPricing.Strategy.FLAT, cfg.strategy());
        assertEquals(LlmPricing.Unit.PER_1K, cfg.unit());
    }

    @Test
    void fromMetadata_shouldParseCostsAcrossTypes_andFilterAndSortTiers() {
        Map<String, Object> p = new HashMap<>();
        p.put("strategy", "tiered");
        p.put("unit", "per_1m");

        p.put("defaultInputCostPerUnit", 2);
        p.put("defaultOutputCostPerUnit", 0.5d);
        p.put("nonThinkingInputCostPerUnit", " 1.25 ");
        p.put("nonThinkingOutputCostPerUnit", "not-a-decimal");
        p.put("thinkingInputCostPerUnit", "   ");
        p.put("thinkingOutputCostPerUnit", new Object());

        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("upToTokens", "2000");
        tier1.put("inputCostPerUnit", "0.10");
        tier1.put("outputCostPerUnit", BigDecimal.valueOf(0.20));

        Map<String, Object> tier2 = new HashMap<>();
        tier2.put("upToTokens", 1000);
        tier2.put("inputCostPerUnit", 3L);
        tier2.put("outputCostPerUnit", 4.5f);

        List<Object> tiers = new ArrayList<>();
        tiers.add("not-a-map");
        Map<String, Object> badUpTo = new HashMap<>();
        badUpTo.put("upToTokens", "abc");
        tiers.add(badUpTo);
        tiers.add(new HashMap<>());
        tiers.add(tier1);
        tiers.add(tier2);
        p.put("tiers", tiers);

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(LlmPricing.Strategy.TIERED, cfg.strategy());
        assertEquals(LlmPricing.Unit.PER_1M, cfg.unit());

        assertEquals(BigDecimal.valueOf(2), cfg.defaultInputCostPerUnit());
        assertEquals(new BigDecimal("0.5"), cfg.defaultOutputCostPerUnit());
        assertEquals(new BigDecimal("1.25"), cfg.nonThinkingInputCostPerUnit());
        assertNull(cfg.nonThinkingOutputCostPerUnit());
        assertNull(cfg.thinkingInputCostPerUnit());
        assertNull(cfg.thinkingOutputCostPerUnit());

        assertNotNull(cfg.tiers());
        assertEquals(2, cfg.tiers().size());
        assertEquals(1000, cfg.tiers().get(0).upToTokens());
        assertEquals(2000, cfg.tiers().get(1).upToTokens());

        assertEquals(BigDecimal.valueOf(3), cfg.tiers().get(0).inputCostPerUnit());
        assertEquals(new BigDecimal("4.5"), cfg.tiers().get(0).outputCostPerUnit());
        assertEquals(new BigDecimal("0.10"), cfg.tiers().get(1).inputCostPerUnit());
        assertEquals(new BigDecimal("0.2"), cfg.tiers().get(1).outputCostPerUnit());
    }

    @Test
    void fromMetadata_shouldParseIntegerFamiliesInAsBigDecimal() {
        Map<String, Object> p = new HashMap<>();
        p.put("defaultInputCostPerUnit", (byte) 1);
        p.put("defaultOutputCostPerUnit", (short) 2);
        p.put("nonThinkingInputCostPerUnit", 3L);

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertEquals(BigDecimal.valueOf(1), cfg.defaultInputCostPerUnit());
        assertEquals(BigDecimal.valueOf(2), cfg.defaultOutputCostPerUnit());
        assertEquals(BigDecimal.valueOf(3), cfg.nonThinkingInputCostPerUnit());
    }

    @Test
    void fromMetadata_shouldReturnConfig_whenAllCostsNullButTiersPresent() {
        Map<String, Object> p = new HashMap<>();
        p.put("strategy", "tiered");
        p.put("unit", "per_1k");

        Map<String, Object> tier = new HashMap<>();
        tier.put("upToTokens", 1000);
        tier.put("inputCostPerUnit", " ");
        tier.put("outputCostPerUnit", " ");

        p.put("tiers", List.of(tier));

        Map<String, Object> m = new HashMap<>();
        m.put("pricing", p);

        LlmPricing.Config cfg = LlmPricing.fromMetadata(m);
        assertNotNull(cfg);
        assertNotNull(cfg.tiers());
        assertEquals(1, cfg.tiers().size());
    }

    @Test
    void isConfiguredForMode_shouldReturnFalse_whenCfgNull() {
        assertFalse(LlmPricing.isConfiguredForMode(null, LlmPricing.Mode.DEFAULT));
    }

    @Test
    void isConfiguredForMode_shouldUseTiersWhenTiered() {
        List<LlmPricing.Tier> tiers = new ArrayList<>();
        tiers.add(null);
        tiers.add(new LlmPricing.Tier(1000, null, null));
        tiers.add(new LlmPricing.Tier(2000, new BigDecimal("0.01"), null));

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

        assertTrue(LlmPricing.isConfiguredForMode(cfg, LlmPricing.Mode.DEFAULT));
    }

    @Test
    void isConfiguredForMode_shouldReturnTrue_whenTieredTiersNullButResolvedHasPrice() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.TIERED,
                LlmPricing.Unit.PER_1K,
                new BigDecimal("0.01"),
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertTrue(LlmPricing.isConfiguredForMode(cfg, LlmPricing.Mode.DEFAULT));
    }

    @Test
    void isConfiguredForMode_shouldReturnTrue_whenTierHasOnlyOutputPrice() {
        List<LlmPricing.Tier> tiers = new ArrayList<>();
        tiers.add(new LlmPricing.Tier(1000, null, new BigDecimal("0.01")));

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

        assertTrue(LlmPricing.isConfiguredForMode(cfg, LlmPricing.Mode.DEFAULT));
    }

    @Test
    void isConfiguredForMode_shouldFallbackToResolvedPrices_whenTieredButNoTierHasCosts() {
        List<LlmPricing.Tier> tiers = new ArrayList<>();
        tiers.add(null);
        tiers.add(new LlmPricing.Tier(1000, null, null));

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

        assertFalse(LlmPricing.isConfiguredForMode(cfg, LlmPricing.Mode.DEFAULT));
    }

    @Test
    void isConfiguredForMode_shouldHandleNonTieredResolvedInOutCombinations() {
        LlmPricing.Config inOnly = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                new BigDecimal("0.01"),
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertTrue(LlmPricing.isConfiguredForMode(inOnly, LlmPricing.Mode.DEFAULT));

        LlmPricing.Config outOnly = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                null,
                new BigDecimal("0.02"),
                null,
                null,
                null,
                null,
                null
        );
        assertTrue(LlmPricing.isConfiguredForMode(outOnly, LlmPricing.Mode.DEFAULT));

        LlmPricing.Config neither = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertFalse(LlmPricing.isConfiguredForMode(neither, LlmPricing.Mode.DEFAULT));

        LlmPricing.Config nonThinkingOutOnly = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                null,
                null,
                null,
                new BigDecimal("0.03"),
                null,
                null,
                null
        );
        assertTrue(LlmPricing.isConfiguredForMode(nonThinkingOutOnly, LlmPricing.Mode.NON_THINKING));
    }

    @Test
    void resolveInputCostPerUnit_shouldSupportModeOverrideAndFallback() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                new BigDecimal("0.10"),
                null,
                new BigDecimal("0.20"),
                null,
                null,
                null,
                null
        );

        assertNull(LlmPricing.resolveInputCostPerUnit(null, LlmPricing.Mode.DEFAULT));
        assertEquals(new BigDecimal("0.10"), LlmPricing.resolveInputCostPerUnit(cfg, null));
        assertEquals(new BigDecimal("0.20"), LlmPricing.resolveInputCostPerUnit(cfg, LlmPricing.Mode.NON_THINKING));
        assertEquals(new BigDecimal("0.10"), LlmPricing.resolveInputCostPerUnit(cfg, LlmPricing.Mode.THINKING));
    }

    @Test
    void resolveInputCostPerUnit_shouldReturnThinkingOverride_whenPresent() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                new BigDecimal("0.10"),
                null,
                null,
                null,
                new BigDecimal("0.30"),
                null,
                null
        );

        assertEquals(new BigDecimal("0.30"), LlmPricing.resolveInputCostPerUnit(cfg, LlmPricing.Mode.THINKING));
    }

    @Test
    void resolveOutputCostPerUnit_shouldSupportModeOverrideAndFallback() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                null,
                new BigDecimal("0.10"),
                null,
                null,
                null,
                new BigDecimal("0.30"),
                null
        );

        assertNull(LlmPricing.resolveOutputCostPerUnit(null, LlmPricing.Mode.DEFAULT));
        assertEquals(new BigDecimal("0.10"), LlmPricing.resolveOutputCostPerUnit(cfg, null));
        assertEquals(new BigDecimal("0.10"), LlmPricing.resolveOutputCostPerUnit(cfg, LlmPricing.Mode.NON_THINKING));
        assertEquals(new BigDecimal("0.30"), LlmPricing.resolveOutputCostPerUnit(cfg, LlmPricing.Mode.THINKING));
    }

    @Test
    void resolveOutputCostPerUnit_shouldReturnNonThinkingOverride_whenPresent() {
        LlmPricing.Config cfg = new LlmPricing.Config(
                LlmPricing.Strategy.FLAT,
                LlmPricing.Unit.PER_1K,
                null,
                new BigDecimal("0.10"),
                null,
                new BigDecimal("0.20"),
                null,
                null,
                null
        );

        assertEquals(new BigDecimal("0.20"), LlmPricing.resolveOutputCostPerUnit(cfg, LlmPricing.Mode.NON_THINKING));
    }
}
