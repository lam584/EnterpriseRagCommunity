package com.example.EnterpriseRagCommunity.service.ai;

final class AiChatSseSupport {

    private AiChatSseSupport() {
    }

    static String extractDeltaContent(String json) {
        return sanitizeMarker(extractDeltaStringField(json, "content"));
    }

    static String extractDeltaReasoningContent(String json) {
        return sanitizeMarker(extractDeltaStringField(json, "reasoning_content"));
    }

    private static String sanitizeMarker(String s) {
        if (s == null) return null;
        if (s.equals("reasoning_content")) return "";
        return s;
    }

    static String extractDeltaStringField(String json, String field) {
        return decodeEscapedContent(JsonStringFieldSupport.extractStringField(json, field));
    }

    static String decodeEscapedContent(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char n = text.charAt(i + 1);
                switch (n) {
                    case '"' -> {
                        out.append('"');
                        i += 2;
                        continue;
                    }
                    case '\\' -> {
                        out.append('\\');
                        i += 2;
                        continue;
                    }
                    case '/' -> {
                        out.append('/');
                        i += 2;
                        continue;
                    }
                    case 'b' -> {
                        out.append('\b');
                        i += 2;
                        continue;
                    }
                    case 'f' -> {
                        out.append('\f');
                        i += 2;
                        continue;
                    }
                    case 'n' -> {
                        out.append('\n');
                        i += 2;
                        continue;
                    }
                    case 'r' -> {
                        out.append('\r');
                        i += 2;
                        continue;
                    }
                    case 't' -> {
                        out.append('\t');
                        i += 2;
                        continue;
                    }
                    case 'u' -> {
                        if (i + 5 < text.length()) {
                            String hex = text.substring(i + 2, i + 6);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (Exception ignore) {
                            }
                            i += 6;
                            continue;
                        }
                        i += 2;
                        continue;
                    }
                    default -> {
                        out.append(n);
                        i += 2;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
