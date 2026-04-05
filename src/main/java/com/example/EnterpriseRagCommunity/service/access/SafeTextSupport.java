package com.example.EnterpriseRagCommunity.service.access;

public final class SafeTextSupport {

    private SafeTextSupport() {
    }

    public static String safeText(String value, int maxLen) {
        if (value == null) return null;
        String text = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (text.isBlank()) return null;
        if (maxLen <= 0) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
