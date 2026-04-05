package com.example.EnterpriseRagCommunity.service.ai;

import java.math.BigDecimal;

public final class LlmPricingValueSupport {

    private LlmPricingValueSupport() {
    }

    public static BigDecimal asBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) {
            if (v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long) {
                return BigDecimal.valueOf(n.longValue());
            }
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return new BigDecimal(t);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
