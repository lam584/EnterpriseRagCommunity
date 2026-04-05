package com.example.EnterpriseRagCommunity.service.ai;

public final class LlmTextSupport {

    private LlmTextSupport() {
    }

    public static int indexOfIgnoreCase(String haystack, String needle, int fromIndex) {
        if (haystack == null || needle == null) return -1;
        int start = Math.max(0, fromIndex);
        if (needle.isEmpty()) return start <= haystack.length() ? start : -1;
        int n = haystack.length();
        int m = needle.length();
        if (m > n) return -1;
        for (int i = start; i + m <= n; i++) {
            boolean ok = true;
            for (int j = 0; j < m; j++) {
                char a = haystack.charAt(i + j);
                char b = needle.charAt(j);
                if (a == b) continue;
                if (Character.toLowerCase(a) != Character.toLowerCase(b)) {
                    ok = false;
                    break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }

    public static String removeMarkerWordIgnoreCase(String text, String marker) {
        if (text == null || text.isBlank()) return text;
        if (marker == null || marker.isBlank()) return text;
        String m = marker.trim();
        int first = indexOfIgnoreCase(text, m, 0);
        if (first < 0) return text;
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (true) {
            int idx = indexOfIgnoreCase(text, m, i);
            if (idx < 0) {
                sb.append(text, i, text.length());
                break;
            }
            sb.append(text, i, idx);
            i = idx + m.length();
        }
        return sb.toString();
    }

    public static String stripReasoningArtifacts(String text) {
        if (text == null || text.isBlank()) return text;
        String t = removeMarkerWordIgnoreCase(text, "reasoning_content");
        t = removeMarkerWordIgnoreCase(t, "<reasoning_content>");
        t = removeMarkerWordIgnoreCase(t, "</reasoning_content>");
        t = removeMarkerWordIgnoreCase(t, "&lt;reasoning_content&gt;");
        t = removeMarkerWordIgnoreCase(t, "&lt;/reasoning_content&gt;");
        return t;
    }
}
