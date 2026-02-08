package com.example.EnterpriseRagCommunity.service.monitor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class LlmPricing {
    private LlmPricing() {
    }

    public enum Mode {
        DEFAULT,
        NON_THINKING,
        THINKING;

        public static Mode fromNullableString(String v) {
            if (v == null) return null;
            String s = v.trim();
            if (s.isBlank()) return null;
            try {
                return Mode.valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }

    public enum Unit {
        PER_1K,
        PER_1M;

        public static Unit fromNullableString(String v) {
            if (v == null) return null;
            String s = v.trim();
            if (s.isBlank()) return null;
            try {
                return Unit.valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }

    public enum Strategy {
        FLAT,
        TIERED;

        public static Strategy fromNullableString(String v) {
            if (v == null) return null;
            String s = v.trim();
            if (s.isBlank()) return null;
            try {
                return Strategy.valueOf(s.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }

    public record Tier(
            long upToTokens,
            BigDecimal inputCostPerUnit,
            BigDecimal outputCostPerUnit
    ) {
    }

    public record Config(
            Strategy strategy,
            Unit unit,
            BigDecimal defaultInputCostPerUnit,
            BigDecimal defaultOutputCostPerUnit,
            BigDecimal nonThinkingInputCostPerUnit,
            BigDecimal nonThinkingOutputCostPerUnit,
            BigDecimal thinkingInputCostPerUnit,
            BigDecimal thinkingOutputCostPerUnit,
            List<Tier> tiers
    ) {
    }

    public static Config fromLegacy(BigDecimal inputCostPer1k, BigDecimal outputCostPer1k) {
        if (inputCostPer1k == null && outputCostPer1k == null) return null;
        return new Config(
                Strategy.FLAT,
                Unit.PER_1K,
                inputCostPer1k,
                outputCostPer1k,
                null,
                null,
                null,
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    public static Config fromMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        Object pricing = metadata.get("pricing");
        if (!(pricing instanceof Map<?, ?> pmap)) return null;

        Strategy strategy = Strategy.fromNullableString(asString(pmap.get("strategy")));
        Unit unit = Unit.fromNullableString(asString(pmap.get("unit")));
        if (strategy == null) strategy = Strategy.FLAT;
        if (unit == null) unit = Unit.PER_1K;

        BigDecimal defaultIn = asBigDecimal(pmap.get("defaultInputCostPerUnit"));
        BigDecimal defaultOut = asBigDecimal(pmap.get("defaultOutputCostPerUnit"));
        BigDecimal nonThinkingIn = asBigDecimal(pmap.get("nonThinkingInputCostPerUnit"));
        BigDecimal nonThinkingOut = asBigDecimal(pmap.get("nonThinkingOutputCostPerUnit"));
        BigDecimal thinkingIn = asBigDecimal(pmap.get("thinkingInputCostPerUnit"));
        BigDecimal thinkingOut = asBigDecimal(pmap.get("thinkingOutputCostPerUnit"));

        List<Tier> tiers = null;
        Object t = pmap.get("tiers");
        if (t instanceof List<?> list && !list.isEmpty()) {
            List<Tier> tmp = new ArrayList<>();
            for (Object x : list) {
                if (!(x instanceof Map<?, ?> tm)) continue;
                Long upTo = asLong(tm.get("upToTokens"));
                if (upTo == null || upTo <= 0) continue;
                BigDecimal in = asBigDecimal(tm.get("inputCostPerUnit"));
                BigDecimal out = asBigDecimal(tm.get("outputCostPerUnit"));
                tmp.add(new Tier(upTo, in, out));
            }
            tmp.sort(Comparator.comparingLong(Tier::upToTokens));
            if (!tmp.isEmpty()) tiers = tmp;
        }

        if (defaultIn == null
                && defaultOut == null
                && nonThinkingIn == null
                && nonThinkingOut == null
                && thinkingIn == null
                && thinkingOut == null
                && (tiers == null || tiers.isEmpty())) {
            return null;
        }

        return new Config(strategy, unit, defaultIn, defaultOut, nonThinkingIn, nonThinkingOut, thinkingIn, thinkingOut, tiers);
    }

    public static boolean isConfiguredForMode(Config cfg, Mode mode) {
        if (cfg == null) return false;
        if (cfg.strategy == Strategy.TIERED) {
            if (cfg.tiers != null) {
                for (Tier t : cfg.tiers) {
                    if (t == null) continue;
                    if (t.inputCostPerUnit != null || t.outputCostPerUnit != null) return true;
                }
            }
        }
        BigDecimal in = resolveInputCostPerUnit(cfg, mode);
        BigDecimal out = resolveOutputCostPerUnit(cfg, mode);
        return in != null || out != null;
    }

    public static BigDecimal resolveInputCostPerUnit(Config cfg, Mode mode) {
        if (cfg == null) return null;
        Mode m = mode == null ? Mode.DEFAULT : mode;
        if (m == Mode.NON_THINKING && cfg.nonThinkingInputCostPerUnit != null) return cfg.nonThinkingInputCostPerUnit;
        if (m == Mode.THINKING && cfg.thinkingInputCostPerUnit != null) return cfg.thinkingInputCostPerUnit;
        return cfg.defaultInputCostPerUnit;
    }

    public static BigDecimal resolveOutputCostPerUnit(Config cfg, Mode mode) {
        if (cfg == null) return null;
        Mode m = mode == null ? Mode.DEFAULT : mode;
        if (m == Mode.NON_THINKING && cfg.nonThinkingOutputCostPerUnit != null) return cfg.nonThinkingOutputCostPerUnit;
        if (m == Mode.THINKING && cfg.thinkingOutputCostPerUnit != null) return cfg.thinkingOutputCostPerUnit;
        return cfg.defaultOutputCostPerUnit;
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal asBigDecimal(Object v) {
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

