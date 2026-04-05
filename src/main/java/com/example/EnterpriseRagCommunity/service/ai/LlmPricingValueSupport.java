package com.example.EnterpriseRagCommunity.service.ai;

import java.math.BigDecimal;

public final class LlmPricingValueSupport {

    private LlmPricingValueSupport() {
    }

    public static BigDecimal asBigDecimal(Object v) {
        switch (v) {
            case null -> {
                return null;
            }
            case BigDecimal bd -> {
                return bd;
            }
            case Number n -> {
                if (v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long) {
                    return BigDecimal.valueOf(n.longValue());
                }
                return BigDecimal.valueOf(n.doubleValue());
            }
            case String s -> {
                String t = s.trim();
                if (t.isBlank()) return null;
                try {
                    return new BigDecimal(t);
                } catch (Exception e) {
                    return null;
                }
            }
            default -> {
            }
        }
        return null;
    }
}
