package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class LlmGatewaySupport {

    private LlmGatewaySupport() {
    }

    static boolean shouldStripThinkBlocks(Boolean enableThinking) {
        return Boolean.FALSE.equals(enableThinking);
    }

    static boolean shouldPreferTokenizerIn(LlmQueueTaskType taskType) {
        if (taskType == null) return false;
        return taskType == LlmQueueTaskType.MULTIMODAL_MODERATION || taskType == LlmQueueTaskType.MODERATION_CHUNK;
    }

    static boolean supportsThinkingDirectiveModel(String modelName) {
        String raw = modelName == null ? "" : modelName.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) return false;

        String base = raw;
        int slash = base.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < base.length()) base = base.substring(slash + 1);
        int colon = base.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < base.length()) base = base.substring(colon + 1);

        if (raw.contains("thinking") || base.contains("thinking")) return false;
        if (base.startsWith("qwen3-") || raw.startsWith("qwen3-")) return true;
        return base.startsWith("qwen-plus-2025-04-28")
                || base.startsWith("qwen-turbo-2025-04-28")
                || raw.startsWith("qwen-plus-2025-04-28")
                || raw.startsWith("qwen-turbo-2025-04-28");
    }

    static String applyThinkingDirective(String content, boolean enableThinking, String modelName) {
        String text = content == null ? "" : content;
        if (!supportsThinkingDirectiveModel(modelName)) return text;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("/no_think") || lower.contains("/think")) return text;
        String directive = enableThinking ? "/think" : "/no_think";
        if (text.endsWith("\n") || text.endsWith("\r")) return text + directive;
        return text + "\n" + directive;
    }

    static List<ChatMessage> applyThinkingDirectiveToMessages(List<ChatMessage> messages, Boolean enableThinking, String modelName) {
        if (messages == null || messages.isEmpty()) return messages;
        if (enableThinking == null) return messages;
        if (!supportsThinkingDirectiveModel(modelName)) return messages;

        int lastUserIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m == null) continue;
            String role = m.role();
            if (role == null) continue;
            if ("user".equalsIgnoreCase(role)) {
                lastUserIndex = i;
                break;
            }
        }
        if (lastUserIndex < 0) return messages;
        ChatMessage lastUser = messages.get(lastUserIndex);
        Object contentObj = lastUser == null ? null : lastUser.content();
        if (!(contentObj instanceof String content)) return messages;

        String patched = applyThinkingDirective(content, Boolean.TRUE.equals(enableThinking), modelName);
        if (patched.equals(content)) return messages;

        java.util.ArrayList<ChatMessage> next = new java.util.ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (i == lastUserIndex && m != null) {
                next.add(new ChatMessage(m.role(), patched));
            } else {
                next.add(m);
            }
        }
        return java.util.Collections.unmodifiableList(next);
    }

    static int indexOfIgnoreCase(String haystack, String needle, int fromIndex) {
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

    static int minPositive(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    static String stripThinkBlocks(String text) {
        if (text == null || text.isBlank()) return text;
        String t = removeClosedThinkBlocks(text);
        int start = minPositive(
                indexOfIgnoreCase(t, "<think", 0),
                indexOfIgnoreCase(t, "&lt;think", 0)
        );
        if (start >= 0) return t.substring(0, start);
        return t;
    }

    static String removeMarkerWordIgnoreCase(String text, String marker) {
        if (text == null || text.isBlank()) return text;
        if (marker == null || marker.isBlank()) return text;
        String t = text;
        String m = marker.trim();
        int first = indexOfIgnoreCase(t, m, 0);
        if (first < 0) return t;
        StringBuilder sb = new StringBuilder(t.length());
        int i = 0;
        while (true) {
            int idx = indexOfIgnoreCase(t, m, i);
            if (idx < 0) {
                sb.append(t, i, t.length());
                break;
            }
            sb.append(t, i, idx);
            i = idx + m.length();
        }
        return sb.toString();
    }

    static String removeClosedReasoningBlocks(String text) {
        if (text == null || text.isBlank()) return text;
        String t = text;
        for (int guard = 0; guard < 50; guard++) {
            int s1 = indexOfIgnoreCase(t, "<reasoning_content", 0);
            int s2 = indexOfIgnoreCase(t, "&lt;reasoning_content", 0);
            int start = minPositive(s1, s2);
            if (start < 0) return t;
            boolean escaped = s2 >= 0 && start == s2;

            int openEndExclusive;
            if (escaped) {
                int gt = indexOfIgnoreCase(t, "&gt;", start);
                if (gt < 0) return t;
                openEndExclusive = gt + "&gt;".length();
            } else {
                int gt = t.indexOf('>', start);
                if (gt < 0) return t;
                openEndExclusive = gt + 1;
            }

            int closeStart = escaped
                    ? indexOfIgnoreCase(t, "&lt;/reasoning_content&gt;", openEndExclusive)
                    : indexOfIgnoreCase(t, "</reasoning_content>", openEndExclusive);
            if (closeStart < 0) return t;
            int closeEndExclusive = closeStart + (escaped ? "&lt;/reasoning_content&gt;".length() : "</reasoning_content>".length());

            int after = closeEndExclusive;
            while (after < t.length() && Character.isWhitespace(t.charAt(after))) after++;
            t = t.substring(0, start) + t.substring(after);
        }
        return t;
    }

    static String stripReasoningArtifacts(String text) {
        if (text == null || text.isBlank()) return text;
        String t = removeClosedReasoningBlocks(text);
        t = removeMarkerWordIgnoreCase(t, "reasoning_content");
        return t;
    }

    static String removeClosedThinkBlocks(String text) {
        if (text == null || text.isBlank()) return text;
        String t = text;
        for (int guard = 0; guard < 50; guard++) {
            int s1 = indexOfIgnoreCase(t, "<think", 0);
            int s2 = indexOfIgnoreCase(t, "&lt;think", 0);
            int start = minPositive(s1, s2);
            if (start < 0) return t;
            boolean escaped = s2 >= 0 && start == s2;

            int openEndExclusive;
            if (escaped) {
                int gt = indexOfIgnoreCase(t, "&gt;", start);
                if (gt < 0) return t;
                openEndExclusive = gt + "&gt;".length();
            } else {
                int gt = t.indexOf('>', start);
                if (gt < 0) return t;
                openEndExclusive = gt + 1;
            }

            int closeStart = escaped
                    ? indexOfIgnoreCase(t, "&lt;/think&gt;", openEndExclusive)
                    : indexOfIgnoreCase(t, "</think>", openEndExclusive);
            if (closeStart < 0) return t;
            int closeEndExclusive = closeStart + (escaped ? "&lt;/think&gt;".length() : "</think>".length());

            int after = closeEndExclusive;
            while (after < t.length() && Character.isWhitespace(t.charAt(after))) after++;
            t = t.substring(0, start) + t.substring(after);
        }
        return t;
    }

    static Map<String, String> mergeHeaders(Map<String, String> base, Map<String, String> extra) {
        if (extra == null || extra.isEmpty()) return base;
        if (base == null || base.isEmpty()) return extra;
        var merged = new LinkedHashMap<>(base);
        merged.putAll(extra);
        return merged;
    }

    static long elapsedMs(long startedNs) {
        long ns = System.nanoTime() - startedNs;
        if (ns <= 0) return 0L;
        return ns / 1_000_000L;
    }

    static String safeErrorCode(Throwable e) {
        if (e == null) return "";
        String c = extractErrorCode(e);
        return c == null ? "" : c;
    }

    static String safeErrorMessage(Throwable e) {
        if (e == null) return "";
        String m = e.getMessage();
        if (m == null) return "";
        String t = m.trim();
        if (t.length() <= 500) return t;
        return t.substring(0, 500);
    }

    static boolean shouldSendDashscopeThinking(AiProvidersConfigService.ResolvedProvider provider) {
        String baseUrl = provider == null ? null : provider.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) return false;
        String u = baseUrl.trim().toLowerCase(Locale.ROOT);
        return u.contains("dashscope.aliyuncs.com") || u.contains("dashscope-intl.aliyuncs.com");
    }

    static Map<String, Object> filterExtraBody(AiProvidersConfigService.ResolvedProvider provider, Map<String, Object> extraBody) {
        if (extraBody == null || extraBody.isEmpty()) return null;
        String baseUrl = provider == null ? null : provider.baseUrl();
        boolean isDashscope = false;
        if (baseUrl != null && !baseUrl.isBlank()) {
            String u = baseUrl.trim().toLowerCase(Locale.ROOT);
            isDashscope = u.contains("dashscope.aliyuncs.com") || u.contains("dashscope-intl.aliyuncs.com");
        }
        if (isDashscope) return extraBody;

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : extraBody.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isBlank()) continue;
            String kn = k.trim();
            if (kn.equals("vl_high_resolution_images")) continue;
            out.put(kn, e.getValue());
        }
        return out.isEmpty() ? null : out;
    }

    static boolean isRetriable(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) return true;
            if (cur instanceof ConnectException) return true;
            if (cur instanceof UnknownHostException) return true;
            if (cur instanceof IOException) {
                String msg = cur.getMessage();
                if (msg != null) {
                    if (msg.contains("HTTP 429")) return true;
                    if (msg.contains("HTTP 5")) return true;
                    if (msg.contains("Connection reset")) return true;
                    if (msg.contains("timed out")) return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    static String extractErrorCode(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (msg.contains("HTTP 429")) return "429";
                if (msg.contains("HTTP 5")) return "5xx";
                if (msg.toLowerCase(Locale.ROOT).contains("rate limit")) return "429";
                if (msg.toLowerCase(Locale.ROOT).contains("too many requests")) return "429";
                if (msg.contains("Connection reset")) return "reset";
                if (msg.toLowerCase(Locale.ROOT).contains("timed out")) return "timeout";
            }
            if (cur instanceof SocketTimeoutException) return "timeout";
            if (cur instanceof ConnectException) return "connect";
            if (cur instanceof UnknownHostException) return "dns";
            cur = cur.getCause();
        }
        return "";
    }

    static Integer asIntLoose(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isNumber()) return n.asInt();
        if (n.isTextual()) {
            String s = n.asText();
            if (s == null) return null;
            String t = s.trim();
            if (t.isEmpty()) return null;
            try {
                return Integer.parseInt(t);
            } catch (Exception ignore) {
            }
            try {
                return (int) Double.parseDouble(t);
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    static Integer pickIntLoose(JsonNode obj, String... keys) {
        if (obj == null || !obj.isObject() || keys == null) return null;
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            Integer v = asIntLoose(obj.path(k));
            if (v != null) return v;
        }
        return null;
    }

    static LlmCallQueueService.UsageMetrics normalizeOpenAiCompatUsage(Integer prompt, Integer completion, Integer total) {
        Integer p = prompt;
        Integer c = completion;
        Integer t = total;
        if (p != null && p < 0) p = null;
        if (c != null && c < 0) c = null;
        if (t != null && t < 0) t = null;

        if (p != null && c != null && t != null) {
            if (t < p) {
                c = t;
                t = p + c;
            } else if (c <= 0 && t - p > 0) {
                c = t - p;
            } else {
                t = p + c;
            }
        } else if (p != null && c != null) {
            t = p + c;
        } else if (p != null && t != null) {
            if (t >= p) {
                c = t - p;
            } else {
                c = t;
                t = p + c;
            }
        } else if (p == null && c != null && t != null) {
            if (t >= c) p = t - c;
        }

        if (p == null && c == null && t == null) return null;
        return new LlmCallQueueService.UsageMetrics(p, c, t, null);
    }

    static boolean isUsageIncomplete(LlmCallQueueService.UsageMetrics usage) {
        return usage == null || usage.promptTokens() == null || usage.totalTokens() == null || usage.completionTokens() == null;
    }

    static Integer usageTotalOrFallback(LlmCallQueueService.UsageMetrics usage, Integer fallbackTotal) {
        Integer total = usage == null ? null : usage.totalTokens();
        return total != null ? total : fallbackTotal;
    }

    static int estimateTokens(long chars) {
        if (chars <= 0) return 0;
        return (int) Math.max(1, (chars + 3) / 4);
    }

    static int estimateInputTokens(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        long chars = 0;
        for (ChatMessage m : messages) {
            if (m == null) continue;
            String role = m.role();
            Object content = m.content();
            if (role != null) chars += role.length();
            switch (content) {
                case null -> {
                    continue;
                }
                case String s -> {
                    chars += s.length();
                    continue;
                }
                case List<?> parts -> {
                    for (Object p : parts) {
                        if (p == null) continue;
                        if (p instanceof Map<?, ?> pm) {
                            Object t = pm.get("text");
                            if (t instanceof String ts) chars += ts.length();
                            Object iu = pm.get("image_url");
                            if (iu instanceof Map<?, ?> ium) {
                                Object u = ium.get("url");
                                if (u instanceof String us) chars += us.length();
                            }
                        } else {
                            chars += String.valueOf(p).length();
                        }
                    }
                    continue;
                }
                default -> {
                }
            }
            chars += String.valueOf(content).length();
        }
        return estimateTokens(chars);
    }

    static String sanitizeMarker(String s) {
        if (s == null) return null;
        String t = s;
        if (t.equals("reasoning_content")) return "";
        String trimmed = t.trim();
        if (trimmed.isEmpty()) return t;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "reasoning_content" -> {
                return "";
            }
            case "<reasoning_content>", "</reasoning_content>" -> {
                return "";
            }
            case "&lt;reasoning_content&gt;", "&lt;/reasoning_content&gt;" -> {
                return "";
            }
        }
        if (lower.startsWith("reasoning_content") && removeMarkerWordIgnoreCase(trimmed, "reasoning_content").trim().isEmpty())
            return "";
        return t;
    }
}
