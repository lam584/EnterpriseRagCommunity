package com.example.EnterpriseRagCommunity.service.monitor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}
