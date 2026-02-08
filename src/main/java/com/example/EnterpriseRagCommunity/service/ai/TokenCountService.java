package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TokenCountService {

    private static final Pattern THINK_BLOCK_PATTERN = Pattern.compile("(?is)<think\\b[^>]*>.*?</think>\\s*");
    private static final Pattern OK_LIKE_PATTERN = Pattern.compile("(?i)^ok(?:[\\s\\p{Punct}。！？、，．。｡]*)$");

    private final OpenSearchTokenizeService openSearchTokenizeService;

    public record NormalizedOutput(
            String rawText,
            String displayText,
            String tokenText,
            boolean strippedThink,
            boolean strippedWhitespace
    ) {
    }

    public record TokenDecision(
            Integer tokensIn,
            Integer tokensOut,
            Integer totalTokens,
            NormalizedOutput normalizedOutput,
            String tokensOutSource
    ) {
    }

    public NormalizedOutput normalizeOutputText(String rawText, boolean enableThinking) {
        String raw = rawText == null ? "" : rawText;
        String display = raw;
        String tokenText = raw;
        boolean strippedThink = false;
        boolean strippedWhitespace = false;

        if (!enableThinking) {
            String beforeStrip = display;
            display = stripReasoningArtifacts(stripThinkBlocks(display));
            strippedThink = !Objects.equals(beforeStrip, display);

            String beforeTrim = display;
            display = display == null ? "" : display.trim();
            strippedWhitespace = !Objects.equals(beforeTrim, display);

            tokenText = normalizeTokenText(display);
        }

        return new NormalizedOutput(raw, display, tokenText, strippedThink, strippedWhitespace);
    }

    private static String normalizeTokenText(String displayText) {
        String t = displayText == null ? "" : displayText.trim();
        if (t.isEmpty()) return "";
        if (OK_LIKE_PATTERN.matcher(t).matches()) return "ok";
        return t;
    }

    public Integer countTextTokens(String text) {
        String t = text == null ? null : text.trim();
        if (t == null || t.isEmpty()) return null;
        try {
            OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
            req.setText(t);
            OpenSearchTokenizeResponse resp = openSearchTokenizeService.tokenize(req);
            if (resp == null || resp.getUsage() == null) return null;
            return resp.getUsage().getInputTokens();
        } catch (Exception e) {
            return null;
        }
    }

    public Integer countChatMessagesTokens(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        try {
            List<OpenSearchTokenizeRequest.Message> list = new ArrayList<>(messages.size());
            for (ChatMessage m : messages) {
                if (m == null) continue;
                String role = m.role();
                if (role == null || role.isBlank()) continue;
                OpenSearchTokenizeRequest.Message mm = new OpenSearchTokenizeRequest.Message();
                mm.setRole(role);
                Object content = m.content();
                if (content instanceof String s) {
                    mm.setContent(s);
                } else if (content instanceof List<?> parts) {
                    StringBuilder sb = new StringBuilder();
                    for (Object p : parts) {
                        if (p == null) continue;
                        if (p instanceof Map<?, ?> pm) {
                            Object t = pm.get("text");
                            if (t instanceof String ts && !ts.isEmpty()) sb.append(ts);
                            Object iu = pm.get("image_url");
                            if (iu instanceof Map<?, ?> ium) {
                                Object u = ium.get("url");
                                if (u instanceof String us && !us.isEmpty()) {
                                    if (sb.length() > 0) sb.append('\n');
                                    sb.append(us);
                                }
                            }
                        } else {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(String.valueOf(p));
                        }
                    }
                    mm.setContent(sb.toString());
                } else {
                    mm.setContent(content == null ? "" : String.valueOf(content));
                }
                list.add(mm);
            }
            if (list.isEmpty()) return null;
            OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
            req.setMessages(list);
            OpenSearchTokenizeResponse resp = openSearchTokenizeService.tokenize(req);
            if (resp == null || resp.getUsage() == null) return null;
            return resp.getUsage().getInputTokens();
        } catch (Exception e) {
            return null;
        }
    }

    public TokenDecision decideChatTokens(
            String providerId,
            String model,
            boolean enableThinking,
            LlmCallQueueService.UsageMetrics usage,
            List<ChatMessage> messages,
            String assistantRawText
    ) {
        NormalizedOutput norm = normalizeOutputText(assistantRawText, enableThinking);

        Integer tokensIn = usage == null ? null : usage.promptTokens();
        Integer tokensOut = null;
        Integer total = null;

        Integer completion = usage == null ? null : usage.completionTokens();
        Integer estCompletion = usage == null ? null : usage.estimatedCompletionTokens();

        boolean forceTokenizerOut = !enableThinking && (norm.strippedThink() || norm.strippedWhitespace()
                || isNvidiaProvider(providerId)
                || isQwen3Model(model)
                || (completion != null && estCompletion != null && !completion.equals(estCompletion)));

        String outSource = null;

        if (!forceTokenizerOut && completion != null && completion > 0) {
            tokensOut = completion;
            total = usage == null ? null : usage.totalTokens();
            outSource = "USAGE";
        } else {
            Integer out = countTextTokens(norm.tokenText());
            if (out != null) {
                tokensOut = out;
                outSource = "TOKENIZER";
            } else if (!enableThinking && forceTokenizerOut && estCompletion != null && estCompletion > 0) {
                tokensOut = estCompletion;
                outSource = "ESTIMATED";
            } else if (!enableThinking && forceTokenizerOut && "ok".equals(norm.tokenText())) {
                tokensOut = 1;
                outSource = "HEURISTIC_OK";
            } else if (completion != null && completion > 0) {
                tokensOut = completion;
                outSource = "USAGE";
            } else if (estCompletion != null && estCompletion > 0) {
                tokensOut = estCompletion;
                outSource = "ESTIMATED";
            }
        }

        if (tokensIn == null) {
            Integer in = countChatMessagesTokens(messages);
            if (in != null) tokensIn = in;
        }
        Integer usageTotal = usage == null ? null : usage.totalTokens();
        if (tokensIn != null && usageTotal != null && usageTotal > 0 && usageTotal < tokensIn) {
            if (tokensOut == null || usageTotal > tokensOut) {
                tokensOut = usageTotal;
                if (!"TOKENIZER".equals(outSource)) outSource = "USAGE_TOTAL_AS_OUT";
            }
        }
        if (tokensIn != null && tokensOut != null) {
            total = tokensIn + tokensOut;
        } else if (total == null) {
            total = usageTotal;
        }

        return new TokenDecision(tokensIn, tokensOut, total, norm, outSource);
    }

    private static boolean isNvidiaProvider(String providerId) {
        String pid = providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
        return pid.equals("nvidia") || pid.startsWith("nvidia");
    }

    private static boolean isQwen3Model(String model) {
        String m = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
        return m.startsWith("qwen/qwen3") || m.contains("/qwen3") || m.contains("qwen3-");
    }

    private static String stripThinkBlocks(String text) {
        if (text == null || text.isBlank()) return text;
        return THINK_BLOCK_PATTERN.matcher(text).replaceAll("");
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int fromIndex) {
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

    private static String removeMarkerWordIgnoreCase(String text, String marker) {
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

    private static String stripReasoningArtifacts(String text) {
        if (text == null || text.isBlank()) return text;
        String t = removeMarkerWordIgnoreCase(text, "reasoning_content");
        t = removeMarkerWordIgnoreCase(t, "<reasoning_content>");
        t = removeMarkerWordIgnoreCase(t, "</reasoning_content>");
        t = removeMarkerWordIgnoreCase(t, "&lt;reasoning_content&gt;");
        t = removeMarkerWordIgnoreCase(t, "&lt;/reasoning_content&gt;");
        return t;
    }
}
