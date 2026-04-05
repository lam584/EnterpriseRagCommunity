package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.AiChatContextEventsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.AiChatContextEventsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ChatContextGovernanceService {
    private static final int DEFAULT_IMAGE_TOKENS = 1000;

    private final ChatContextGovernanceConfigService configService;
    private final AiChatContextEventsRepository eventsRepository;

    @Data
    public static class ApplyResult {
        private List<ChatMessage> messages;
        private boolean changed;
        private String reason;
        private Integer beforeTokens;
        private Integer afterTokens;
        private Integer beforeChars;
        private Integer afterChars;
        private Map<String, Object> detail;
    }

    private static boolean compressHistory(List<ChatMessage> messages, int systemPrefix, boolean hasUserTail, int keepLast, ChatContextGovernanceConfigDTO cfg, Map<String, Object> detail) {
        int lastIdx = hasUserTail ? messages.size() - 1 : messages.size();
        int start = Math.min(systemPrefix, lastIdx);
        int available = lastIdx - start;
        if (available <= keepLast) return false;

        int compressCount = available - keepLast;
        List<ChatMessage> toCompress = new ArrayList<>(messages.subList(start, start + compressCount));
        List<ChatMessage> keep = new ArrayList<>(messages.subList(start + compressCount, lastIdx));

        int snippetChars = cfg.getCompressionPerMessageSnippetChars() == null ? 200 : Math.max(10, cfg.getCompressionPerMessageSnippetChars());
        int maxChars = cfg.getCompressionMaxChars() == null ? 8000 : Math.max(200, cfg.getCompressionMaxChars());

        String summary = buildSummary(toCompress, snippetChars, maxChars);
        if (summary == null || summary.isBlank()) return false;

        List<String> ops = getOps(detail);
        ops.add(String.format(Locale.ROOT, "compressHistory: %d msgs -> 1 summary", toCompress.size()));

        List<ChatMessage> next = new ArrayList<>(messages.subList(0, start));
        next.add(ChatMessage.system(summary));
        next.addAll(keep);
        if (hasUserTail && lastIdx < messages.size()) {
            next.addAll(messages.subList(lastIdx, messages.size()));
        }
        messages.clear();
        messages.addAll(next);

        Map<String, Object> meta = getMeta(detail);
        meta.put("compressedMessages", toCompress.size());
        meta.put("summaryChars", summary.length());
        return true;
    }

    private static String buildReason(boolean compressed, boolean clipped, int beforeTokens, int afterTokens, int beforeChars, int afterChars) {
        if (compressed && clipped) return "compress+clip";
        if (compressed) return "compress";
        if (clipped) return "clip";
        if (afterTokens != beforeTokens || afterChars != beforeChars) return "adjust";
        return "nochange";
    }

    private static boolean clipToBudget(List<ChatMessage> messages, int systemPrefix, boolean hasUserTail, ChatContextGovernanceConfigDTO cfg, Map<String, Object> detail) {
        if (messages == null || messages.isEmpty()) return false;

        int maxTokens = cfg.getMaxPromptTokens() == null ? 0 : Math.max(0, cfg.getMaxPromptTokens());
        int reserve = cfg.getReserveAnswerTokens() == null ? 0 : Math.max(0, cfg.getReserveAnswerTokens());
        int budgetTokens = maxTokens == 0 ? 0 : Math.max(0, maxTokens - reserve);
        int maxChars = cfg.getMaxPromptChars() == null ? 0 : Math.max(0, cfg.getMaxPromptChars());
        int perMessageMaxTokens = cfg.getPerMessageMaxTokens() == null ? 0 : Math.max(0, cfg.getPerMessageMaxTokens());
        int keepLast = cfg.getKeepLastMessages() == null ? 0 : Math.max(0, cfg.getKeepLastMessages());

        List<String> ops = getOps(detail);
        Map<String, Object> meta = getMeta(detail);

        int beforeTokens = approxTokensOfMessages(messages, DEFAULT_IMAGE_TOKENS);
        int beforeChars = approxCharsOfMessages(messages);

        boolean changed = false;

        if (keepLast > 0) {
            int lastIdx = hasUserTail ? messages.size() - 1 : messages.size();
            int start = Math.min(systemPrefix, lastIdx);
            int available = lastIdx - start;
            if (available > keepLast) {
                int drop = available - keepLast;
                List<Map<String, Object>> dropped = new ArrayList<>();
                for (int i = 0; i < drop; i++) {
                    ChatMessage m = messages.get(start + i);
                    dropped.add(dropMeta(m));
                }
                messages.subList(start, start + drop).clear();
                changed = true;
                ops.add(String.format(Locale.ROOT, "dropHistoryByKeepLast: %d msgs", drop));
                meta.put("droppedByKeepLast", dropped);
            }
        }

        if (perMessageMaxTokens > 0) {
            List<Map<String, Object>> truncated = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage m = messages.get(i);
                if (m == null) continue;
                if (i < systemPrefix) continue;
                if (hasUserTail && i == messages.size() - 1) continue;
                ChatMessage t = truncateMessage(m, perMessageMaxTokens);
                if (t != null && t != m) {
                    messages.set(i, t);
                    changed = true;
                    truncated.add(Map.of(
                            "index", i,
                            "role", m.role(),
                            "beforeChars", safeLen(extractText(m)),
                            "afterChars", safeLen(extractText(t))
                    ));
                }
            }
            if (!truncated.isEmpty()) {
                ops.add(String.format(Locale.ROOT, "truncatePerMessage: %d msgs", truncated.size()));
                meta.put("truncatedPerMessage", truncated);
            }
        }

        boolean allowDropSystem = cfg.getAllowDropRagContext() == null || cfg.getAllowDropRagContext();
        if (budgetTokens > 0) {
            List<Map<String, Object>> dropped = new ArrayList<>();
            while (approxTokensOfMessages(messages, DEFAULT_IMAGE_TOKENS) > budgetTokens) {
                int dropIdx = findOldestDroppableIndex(messages, systemPrefix, allowDropSystem, hasUserTail);
                if (dropIdx < 0) break;
                dropped.add(dropMeta(messages.get(dropIdx)));
                messages.remove(dropIdx);
                changed = true;
            }
            if (!dropped.isEmpty()) {
                ops.add(String.format(Locale.ROOT, "dropToBudgetTokens: %d msgs", dropped.size()));
                meta.put("droppedByBudgetTokens", dropped);
            }
        }

        if (maxChars > 0) {
            int currentChars = approxCharsOfMessages(messages);
            if (currentChars > maxChars) {
                List<Map<String, Object>> truncated = new ArrayList<>();
                for (int i = messages.size() - 1; i >= 0 && currentChars > maxChars; i--) {
                    ChatMessage m = messages.get(i);
                    if (m == null) continue;
                    if (i < systemPrefix) continue;
                    ChatMessage t = truncateMessageByCharsFromEnd(m, Math.max(0, maxChars - (currentChars - safeLen(extractText(m)))));
                    if (t != null && t != m) {
                        messages.set(i, t);
                        truncated.add(Map.of(
                                "index", i,
                                "role", m.role(),
                                "beforeChars", safeLen(extractText(m)),
                                "afterChars", safeLen(extractText(t))
                        ));
                        currentChars = approxCharsOfMessages(messages);
                        changed = true;
                    }
                }
                if (!truncated.isEmpty()) {
                    ops.add(String.format(Locale.ROOT, "truncateToMaxChars: %d msgs", truncated.size()));
                    meta.put("truncatedToMaxChars", truncated);
                }
            }
        }

        int afterTokens = approxTokensOfMessages(messages, DEFAULT_IMAGE_TOKENS);
        int afterChars = approxCharsOfMessages(messages);
        if (afterTokens != beforeTokens || afterChars != beforeChars) changed = true;
        meta.put("budgetTokens", budgetTokens);
        meta.put("maxPromptChars", maxChars);
        meta.put("beforeTokens", beforeTokens);
        meta.put("afterTokens", afterTokens);
        meta.put("beforeChars", beforeChars);
        meta.put("afterChars", afterChars);
        return changed;
    }

    private static String buildSummary(List<ChatMessage> msgs, int snippetChars, int maxChars) {
        if (msgs == null || msgs.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("以下为较早历史消息摘要（自动压缩，仅供参考，必要时请向用户确认）：\n");
        for (ChatMessage m : msgs) {
            if (m == null) continue;
            String role = m.role() == null ? "" : m.role().trim().toLowerCase(Locale.ROOT);
            if ("system".equals(role)) continue;
            String text = extractText(m);
            if (text == null || text.isBlank()) continue;
            String oneLine = text.replace('\r', ' ').replace('\n', ' ').trim();
            if (oneLine.length() > snippetChars) oneLine = oneLine.substring(0, snippetChars);
            String prefix = "user".equals(role) ? "U: " : ("assistant".equals(role) ? "A: " : (role + ": "));
            sb.append("- ").append(prefix).append(oneLine).append('\n');
            if (sb.length() >= maxChars) break;
        }
        String s = sb.toString().trim();
        if (s.length() > maxChars) s = s.substring(0, maxChars);
        return s;
    }

    @Transactional
    public ApplyResult apply(Long userId, Long sessionId, Long questionMessageId, List<ChatMessage> input) {
        ChatContextGovernanceConfigDTO cfg = configService.getConfigOrDefault();
        ApplyResult out = new ApplyResult();
        if (input == null) input = List.of();
        List<ChatMessage> messages = new ArrayList<>(input);

        int beforeTokens = approxTokensOfMessages(messages, DEFAULT_IMAGE_TOKENS);
        int beforeChars = approxCharsOfMessages(messages);
        out.setBeforeTokens(beforeTokens);
        out.setBeforeChars(beforeChars);

        boolean enabled = cfg == null || cfg.getEnabled() == null || cfg.getEnabled();
        if (!enabled || messages.isEmpty()) {
            out.setMessages(messages);
            out.setChanged(false);
            out.setReason("disabled");
            out.setAfterTokens(beforeTokens);
            out.setAfterChars(beforeChars);
            return out;
        }

        long startedAt = System.currentTimeMillis();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("ops", new ArrayList<String>());
        detail.put("beforeTokens", beforeTokens);
        detail.put("beforeChars", beforeChars);

        int systemPrefix = countLeadingSystemMessages(messages);
        boolean hasUserTail = !messages.isEmpty() && "user".equalsIgnoreCase(messages.getLast().role());

        boolean compressed = false;
        if (cfg != null && Boolean.TRUE.equals(cfg.getCompressionEnabled())) {
            int trigger = cfg.getCompressionTriggerTokens() == null ? 0 : Math.max(0, cfg.getCompressionTriggerTokens());
            if (trigger > 0 && beforeTokens > trigger) {
                int keep = cfg.getCompressionKeepLastMessages() == null ? 0 : Math.max(0, cfg.getCompressionKeepLastMessages());
                compressed = compressHistory(messages, systemPrefix, hasUserTail, keep, cfg, detail);
            }
        }

        boolean clipped = clipToBudget(messages, systemPrefix, hasUserTail, cfg, detail);

        int afterTokens = approxTokensOfMessages(messages, DEFAULT_IMAGE_TOKENS);
        int afterChars = approxCharsOfMessages(messages);

        out.setMessages(messages);
        out.setAfterTokens(afterTokens);
        out.setAfterChars(afterChars);

        boolean changed = compressed || clipped || afterTokens != beforeTokens || afterChars != beforeChars;
        out.setChanged(changed);
        out.setReason(buildReason(compressed, clipped, beforeTokens, afterTokens, beforeChars, afterChars));
        out.setDetail(detail);

        if (cfg != null && changed && Boolean.TRUE.equals(cfg.getLogEnabled())) {
            double p = cfg.getLogSampleRate() == null ? 1.0 : cfg.getLogSampleRate();
            p = Math.clamp(p, 0.0, 1.0);
            if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= p) {
                AiChatContextEventsEntity e = new AiChatContextEventsEntity();
                e.setUserId(userId);
                e.setSessionId(sessionId);
                e.setQuestionMessageId(questionMessageId);
                e.setKind("CHAT");
                e.setReason(out.getReason());
                e.setTargetPromptTokens(cfg.getMaxPromptTokens());
                e.setReserveAnswerTokens(cfg.getReserveAnswerTokens());
                e.setBeforeTokens(beforeTokens);
                e.setAfterTokens(afterTokens);
                e.setBeforeChars(beforeChars);
                e.setAfterChars(afterChars);
                e.setLatencyMs((int) Math.max(0, System.currentTimeMillis() - startedAt));
                detail.put("afterTokens", afterTokens);
                detail.put("afterChars", afterChars);
                e.setDetailJson(detail);
                e.setCreatedAt(LocalDateTime.now());
                eventsRepository.save(e);
            }
        }

        return out;
    }

    private static int countLeadingSystemMessages(List<ChatMessage> messages) {
        int n = 0;
        for (ChatMessage m : messages) {
            if (m == null) break;
            if (!"system".equalsIgnoreCase(m.role())) break;
            n++;
        }
        return n;
    }

    private static int findOldestDroppableIndex(List<ChatMessage> messages, int systemPrefix, boolean allowDropSystem, boolean hasUserTail) {
        int lastIdx = hasUserTail ? messages.size() - 1 : messages.size();
        for (int i = systemPrefix; i < lastIdx; i++) {
            ChatMessage m = messages.get(i);
            if (m == null) continue;
            if (!allowDropSystem && "system".equalsIgnoreCase(m.role())) continue;
            return i;
        }
        return -1;
    }

    private static ChatMessage truncateMessage(ChatMessage m, int maxTokens) {
        if (m == null) return null;
        Object c = m.content();
        if (c instanceof String s) {
            int tokens = approxTokens(s);
            if (tokens <= maxTokens) return m;
            String t = truncateByApproxTokens(s, maxTokens);
            return new ChatMessage(m.role(), t);
        }
        if (c instanceof List<?> list) {
            List<Map<String, Object>> parts = safeParts(list);
            if (parts.isEmpty()) return m;
            List<Map<String, Object>> next = new ArrayList<>();
            boolean changed = false;
            for (Map<String, Object> p : parts) {
                if (p == null) continue;
                Object type = p.get("type");
                if ("text".equals(String.valueOf(type))) {
                    String text = p.get("text") == null ? "" : String.valueOf(p.get("text"));
                    int tokens = approxTokens(text);
                    if (tokens > maxTokens) {
                        String t = truncateByApproxTokens(text, maxTokens);
                        Map<String, Object> copy = new LinkedHashMap<>(p);
                        copy.put("text", t);
                        next.add(copy);
                        changed = true;
                    } else {
                        next.add(p);
                    }
                } else {
                    next.add(p);
                }
            }
            if (!changed) return m;
            return new ChatMessage(m.role(), next);
        }
        return m;
    }

    private static ChatMessage truncateMessageByCharsFromEnd(ChatMessage m, int allowedChars) {
        if (m == null) return null;
        if (allowedChars < 0) allowedChars = 0;
        Object c = m.content();
        if (c instanceof String s) {
            if (s.length() <= allowedChars) return m;
            return new ChatMessage(m.role(), s.substring(0, allowedChars));
        }
        if (c instanceof List<?> list) {
            List<Map<String, Object>> parts = safeParts(list);
            if (parts.isEmpty()) return m;
            List<Map<String, Object>> next = new ArrayList<>();
            boolean changed = false;
            for (Map<String, Object> p : parts) {
                if (p == null) continue;
                Object type = p.get("type");
                if ("text".equals(String.valueOf(type))) {
                    String text = p.get("text") == null ? "" : String.valueOf(p.get("text"));
                    if (text.length() > allowedChars) {
                        Map<String, Object> copy = new LinkedHashMap<>(p);
                        copy.put("text", text.substring(0, allowedChars));
                        next.add(copy);
                        changed = true;
                    } else {
                        next.add(p);
                    }
                } else {
                    next.add(p);
                }
            }
            if (!changed) return m;
            return new ChatMessage(m.role(), next);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> safeParts(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static Map<String, Object> dropMeta(ChatMessage m) {
        if (m == null) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("role", null);
            out.put("chars", 0);
            return out;
        }
        String text = extractText(m);
        String snippet = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
        if (snippet.length() > 120) snippet = snippet.substring(0, 120);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", m.role());
        out.put("chars", safeLen(text));
        out.put("snippet", snippet);
        return out;
    }

    private static String extractText(ChatMessage m) {
        if (m == null) return null;
        Object c = m.content();
        if (c instanceof String s) return s;
        if (c instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> map)) continue;
                Object type = map.get("type");
                if (!"text".equals(String.valueOf(type))) continue;
                Object text = map.get("text");
                return text == null ? "" : String.valueOf(text);
            }
            return "";
        }
        return String.valueOf(c);
    }

    private static int approxCharsOfMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int sum = 0;
        for (ChatMessage m : messages) {
            String t = extractText(m);
            sum += safeLen(t);
        }
        return sum;
    }

    private static int approxTokensOfMessages(List<ChatMessage> messages, int imageTokens) {
        if (messages == null || messages.isEmpty()) return 0;
        int sum = 0;
        for (ChatMessage m : messages) {
            if (m == null) continue;
            Object c = m.content();
            if (c instanceof String s) {
                sum += approxTokens(s);
            } else if (c instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> map)) continue;
                    Object type = map.get("type");
                    String t = String.valueOf(type);
                    if ("text".equals(t)) {
                        Object text = map.get("text");
                        sum += approxTokens(text == null ? "" : String.valueOf(text));
                    } else if ("image_url".equals(t)) {
                        sum += imageTokens;
                    }
                }
            } else if (c != null) {
                sum += approxTokens(String.valueOf(c));
            }
        }
        return sum;
    }

    private static int approxTokens(String s) {
        return ApproxTokenSupport.approxTokens(s);
    }

    private static String truncateByApproxTokens(String s, int maxTokens) {
        if (s == null) return "";
        if (maxTokens <= 0) return "";
        double t = 0;
        int end = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            t += (c <= 0x7f) ? 0.25 : 1.0;
            if (t > maxTokens) break;
            end = i + 1;
        }
        return s.substring(0, end);
    }

    @SuppressWarnings("unchecked")
    private static List<String> getOps(Map<String, Object> detail) {
        if (detail == null) return new ArrayList<>();
        Object v = detail.get("ops");
        if (v instanceof List<?> list) return (List<String>) list;
        List<String> out = new ArrayList<>();
        detail.put("ops", out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMeta(Map<String, Object> detail) {
        if (detail == null) return new LinkedHashMap<>();
        Object v = detail.get("meta");
        if (v instanceof Map<?, ?> map) return (Map<String, Object>) map;
        Map<String, Object> out = new LinkedHashMap<>();
        detail.put("meta", out);
        return out;
    }

    private static int safeLen(String s) {
        return s == null ? 0 : s.length();
    }
}
