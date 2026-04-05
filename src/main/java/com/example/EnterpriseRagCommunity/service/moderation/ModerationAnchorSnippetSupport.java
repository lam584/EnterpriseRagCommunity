package com.example.EnterpriseRagCommunity.service.moderation;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

public final class ModerationAnchorSnippetSupport {

    private static final String DEFAULT_BOUNDARY_REGEX = "\\r\\n|\\r|\\n|\\u3002|\\uFF01|\\uFF1F|;|!|\\?|$";

    private ModerationAnchorSnippetSupport() {
    }

    public record AnchorContext(String before, String after) {
    }

    public static AnchorContext readAnchorContext(Map<String, Object> node) {
        if (node == null || node.isEmpty()) return null;
        Object bc = node.get("before_context");
        Object ac = node.get("after_context");
        String before = bc == null ? null : String.valueOf(bc).trim();
        String after = ac == null ? null : String.valueOf(ac).trim();
        if (before == null || before.isBlank()) return null;
        return new AnchorContext(before, after);
    }

    public static String pickBestRegexSnippet(
            Matcher matcher,
            String normText,
            String normBefore,
            Function<String, String> cleaner,
            BiFunction<String, Integer, String> fallback
    ) {
        String best = null;
        int bestLen = Integer.MAX_VALUE;
        boolean matched = false;
        int guard = 0;
        while (matcher.find() && guard < 50) {
            guard += 1;
            matched = true;
            String mid = matcher.group(1);
            if (mid == null) continue;
            String cleaned = cleaner.apply(mid);
            if (cleaned == null || cleaned.isBlank()) continue;
            int len = cleaned.length();
            if (len < bestLen) {
                best = cleaned;
                bestLen = len;
            }
        }
        if (best != null) return best;
        if (matched) return null;
        int beforeIdx = normText.indexOf(normBefore);
        if (beforeIdx < 0) return null;
        return fallback.apply(normText, beforeIdx + normBefore.length());
    }

    public static String extractBetweenAnchorsByRegex(
            String text,
            String before,
            String after,
            int cap,
            Function<String, String> cleaner,
            BiFunction<String, Integer, String> fallback
    ) {
        if (text == null || text.isEmpty() || before == null || before.isBlank()) return null;

        String normText = normalizeForAnchorRegex(text);
        String normBefore = normalizeForAnchorRegex(before);
        String normAfter = after != null && !after.isBlank() ? normalizeForAnchorRegex(after) : null;

        String beforeRegex = anchorToRegex(normBefore);
        if (beforeRegex.isEmpty()) return null;
        String afterRegex = normAfter == null ? "" : anchorToRegex(normAfter);

        Pattern pattern = afterRegex.isEmpty()
                ? Pattern.compile(beforeRegex + "(.{0," + cap + "}?)" + "(?=" + DEFAULT_BOUNDARY_REGEX + ")", Pattern.DOTALL)
                : Pattern.compile(beforeRegex + "(.{0," + cap + "}?)" + afterRegex, Pattern.DOTALL);
        return pickBestRegexSnippet(pattern.matcher(normText), normText, normBefore, cleaner, fallback);
    }

    public static String anchorToRegex(String anchor) {
        if (anchor == null) return "";
        String text = anchor.trim();
        if (text.isEmpty()) return "";
        String[] parts = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append("\\s+");
            sb.append(Pattern.quote(part));
        }
        return sb.toString();
    }

    public static String normalizeForAnchorRegex(String value) {
        if (value == null) return "";
        String text = value.replace('“', '"').replace('”', '"')
                .replace('‘', '\'').replace('’', '\'');
        text = text.replaceAll(" ?\" ?", "\"")
                .replaceAll(" ?' ?", "'");
        return text;
    }
}
