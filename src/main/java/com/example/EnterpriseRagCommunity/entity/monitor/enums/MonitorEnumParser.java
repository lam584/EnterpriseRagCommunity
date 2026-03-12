package com.example.EnterpriseRagCommunity.entity.monitor.enums;

import java.util.Locale;

public final class MonitorEnumParser {
    private MonitorEnumParser() {
    }

    public static <E extends Enum<E>> E fromNullableString(Class<E> enumType, String value) {
        if (enumType == null || value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
