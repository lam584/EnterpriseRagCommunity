package com.example.EnterpriseRagCommunity.service.moderation;

import java.util.function.Function;
import java.util.function.IntPredicate;

public final class ModerationViolationSnippetSupport {

    private ModerationViolationSnippetSupport() {
    }

    public static String fallbackViolationSnippet(String text,
                                                  int violationStart,
                                                  String sectionMarker,
                                                  String doubleNewlineMarker,
                                                  IntPredicate boundaryMatcher,
                                                  Function<String, String> cleaner) {
        if (text == null || text.isEmpty()) return null;
        int start = Math.max(0, violationStart);
        if (start >= text.length()) return null;

        int end = Math.min(start + 220, text.length());
        int imageIdx = text.indexOf("[[IMAGE_", start);
        if (imageIdx >= 0 && imageIdx < end) end = imageIdx;

        if (sectionMarker != null && !sectionMarker.isBlank()) {
            int sectionIdx = text.indexOf(sectionMarker, start);
            if (sectionIdx >= 0 && sectionIdx < end) end = sectionIdx;
        }

        if (doubleNewlineMarker != null && !doubleNewlineMarker.isBlank()) {
            int dblNl = text.indexOf(doubleNewlineMarker, start);
            if (dblNl > start && dblNl < end) end = dblNl;
        }

        int boundary = findBoundaryEnd(text, start, end, boundaryMatcher);
        if (boundary > start + 4 && boundary < end) end = boundary;

        if (end <= start) return null;
        String snippet = text.substring(start, end);
        String cleaned = cleaner == null ? snippet : cleaner.apply(snippet);
        if (cleaned != null && !cleaned.isEmpty()) return cleaned;

        int altEnd = Math.min(start + 80, text.length());
        if (altEnd <= start) return null;
        String alt = text.substring(start, altEnd);
        return cleaner == null ? alt : cleaner.apply(alt);
    }

    private static int findBoundaryEnd(String text, int start, int maxEnd, IntPredicate boundaryMatcher) {
        int end = Math.min(text.length(), maxEnd);
        if (boundaryMatcher == null) return end;
        for (int i = start; i < end; i++) {
            if (boundaryMatcher.test(text.charAt(i))) {
                return i;
            }
        }
        return end;
    }
}
