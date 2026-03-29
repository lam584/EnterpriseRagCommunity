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
        if (json == null) return null;
        String f = field == null ? "" : field.trim();
        if (f.isEmpty()) return null;
        int idx = json.indexOf("\"" + f + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int i = firstQuote + 1;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (esc) {
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (Exception ignore) {
                            }
                            i += 4;
                        }
                    }
                    default -> sb.append(c);
                }
                esc = false;
            } else {
                if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            i++;
        }
        return decodeEscapedContent(sb.toString());
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
