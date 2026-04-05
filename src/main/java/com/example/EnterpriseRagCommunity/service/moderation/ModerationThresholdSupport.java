package com.example.EnterpriseRagCommunity.service.moderation;

import java.util.Locale;

public final class ModerationThresholdSupport {

    private ModerationThresholdSupport() {
    }

    public static boolean asBooleanRequired(Object value, String key) {
        switch (value) {
            case null -> throw new IllegalStateException("missing threshold: " + key);
            case Boolean b -> {
                return b;
            }
            case Number n -> {
                return n.intValue() != 0;
            }
            default -> {
            }
        }
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")) return false;
        throw new IllegalStateException("invalid boolean threshold: " + key);
    }

    public static double asDoubleRequired(Object value, String key) {
        if (value == null) throw new IllegalStateException("missing threshold: " + key);
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new IllegalStateException("invalid double threshold: " + key, e);
        }
    }
}
