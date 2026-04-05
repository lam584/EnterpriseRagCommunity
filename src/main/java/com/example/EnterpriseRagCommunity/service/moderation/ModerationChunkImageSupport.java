package com.example.EnterpriseRagCommunity.service.moderation;

public final class ModerationChunkImageSupport {

    private ModerationChunkImageSupport() {
    }

    public static Integer resolveImageIndex(Object indexValue, String placeholder, java.util.function.Function<Object, Integer> toInt) {
        Integer idx = toInt.apply(indexValue);
        if (idx == null && placeholder != null) {
            idx = parseImageIndexFromPlaceholder(placeholder);
        }
        return idx;
    }

    public static Integer parseImageIndexFromPlaceholder(String placeholder) {
        if (placeholder == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[\\[IMAGE_(\\d+)]]").matcher(placeholder.trim());
        if (!matcher.matches()) return null;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignore) {
            return null;
        }
    }
}
