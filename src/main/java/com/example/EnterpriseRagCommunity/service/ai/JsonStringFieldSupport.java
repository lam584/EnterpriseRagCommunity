package com.example.EnterpriseRagCommunity.service.ai;

public final class JsonStringFieldSupport {

    private JsonStringFieldSupport() {
    }

    public static String extractStringField(String json, String field) {
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
        return sb.toString();
    }
}
