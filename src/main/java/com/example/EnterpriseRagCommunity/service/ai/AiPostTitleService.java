package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostTitleGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostTitleGenHistoryEntity;
import com.example.EnterpriseRagCommunity.service.ai.client.BailianOpenAiSseClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPostTitleService {

    private final AiProperties aiProperties;
    private final PostTitleGenConfigService postTitleGenConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPostTitleSuggestResponse suggestTitles(AiPostTitleSuggestRequest req, Long actorUserId) {
        PostTitleGenConfigEntity cfg = postTitleGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("标题生成已关闭");
        }

        int defaultCount = cfg.getDefaultCount() == null ? PostTitleGenConfigService.DEFAULT_DEFAULT_COUNT : cfg.getDefaultCount();
        int maxCount = cfg.getMaxCount() == null ? PostTitleGenConfigService.DEFAULT_MAX_COUNT : cfg.getMaxCount();

        int count = req.getCount() == null ? defaultCount : req.getCount();
        if (count <= 0) count = defaultCount;
        if (count > maxCount) count = maxCount;

        String model = (cfg.getModel() != null && !cfg.getModel().isBlank()) ? cfg.getModel() : aiProperties.getModel();
        Double temperature = cfg.getTemperature();
        if (temperature == null) temperature = 0.4;

        String content = req.getContent() == null ? "" : req.getContent();
        content = content.trim();
        int contentLen = content.length();

        int maxChars = cfg.getMaxContentChars() == null ? PostTitleGenConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars);
        }

        String userPrompt = renderPrompt(cfg.getPromptTemplate(), count, req.getBoardName(), req.getTags(), content);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", cfg.getSystemPrompt()));
        messages.add(Map.of("role", "user", "content", userPrompt));

        long started = System.currentTimeMillis();
        String rawJson;
        try {
            rawJson = new BailianOpenAiSseClient(aiProperties)
                    .chatCompletionsOnce(null, aiProperties.getBaseUrl(), model, messages, temperature);
        } catch (Exception e) {
            throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
        }

        String assistantText = extractAssistantContent(rawJson);
        List<String> titles = parseTitlesFromAssistantText(assistantText, count);

        long latency = System.currentTimeMillis() - started;

        if (Boolean.TRUE.equals(cfg.getHistoryEnabled()) && actorUserId != null) {
            PostTitleGenHistoryEntity h = new PostTitleGenHistoryEntity();
            h.setUserId(actorUserId);
            h.setCreatedAt(LocalDateTime.now());
            h.setBoardName(blankToNull(req.getBoardName()));
            h.setTagsJson(req.getTags() == null ? List.of() : new ArrayList<>(req.getTags()));
            h.setRequestedCount(count);
            h.setAppliedMaxContentChars(maxChars);
            h.setContentLen(contentLen);
            h.setContentExcerpt(buildExcerpt(req.getContent()));
            h.setTitlesJson(new ArrayList<>(titles));
            h.setModel(model);
            h.setTemperature(temperature);
            h.setLatencyMs(latency);
            h.setPromptVersion(cfg.getVersion());
            postTitleGenConfigService.recordHistory(h);
        }

        return new AiPostTitleSuggestResponse(titles, model, latency);
    }

    private String extractAssistantContent(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                // non-streaming usually: choices[0].message.content
                JsonNode contentNode = first.path("message").path("content");
                if (!contentNode.isMissingNode() && contentNode.isTextual()) {
                    return contentNode.asText();
                }
                // fallback: choices[0].text
                JsonNode textNode = first.path("text");
                if (!textNode.isMissingNode() && textNode.isTextual()) {
                    return textNode.asText();
                }
            }
        } catch (Exception ignore) {
        }
        // last resort: return raw
        return rawJson;
    }

    private List<String> parseTitlesFromAssistantText(String assistantText, int expectedCount) {
        if (assistantText == null) assistantText = "";
        assistantText = assistantText.trim();

        String json = assistantText;
        // Tolerate occasional wrapper text: extract first {...}
        int l = json.indexOf('{');
        int r = json.lastIndexOf('}');
        if (l >= 0 && r > l) {
            json = json.substring(l, r + 1);
        }

        List<String> titles = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("titles");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    if (!n.isTextual()) continue;
                    String t = n.asText();
                    t = cleanTitle(t);
                    if (!t.isBlank()) titles.add(t);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 输出无法解析为标题列表，请重试", e);
        }

        // normalize: dedup + limit
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : titles) {
            if (t.isBlank()) continue;
            set.add(t);
        }

        List<String> out = new ArrayList<>(set);
        if (out.size() > expectedCount) {
            out = out.subList(0, expectedCount);
        }
        return out;
    }

    private String cleanTitle(String t) {
        if (t == null) return "";
        t = t.trim();
        // remove surrounding quotes-like characters
        t = t.replaceAll("^[\"“”]+|[\"“”]+$", "");
        // hard length guard (roughly chars)
        if (t.length() > 50) t = t.substring(0, 50);
        return t.trim();
    }

    private static String buildExcerpt(String content) {
        if (content == null) return null;
        String t = content.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 240) t = t.substring(0, 240);
        return t;
    }

    private static String renderPrompt(String template, int count, String boardName, List<String> tags, String content) {
        String safeTemplate = (template == null || template.isBlank())
                ? PostTitleGenConfigService.DEFAULT_PROMPT_TEMPLATE
                : template;

        String bn = boardName == null ? "" : boardName.trim();
        String boardLine = bn.isBlank() ? "" : ("版块：" + bn + "\n");

        String tagsLine = "";
        if (tags != null && !tags.isEmpty()) {
            List<String> cleaned = new ArrayList<>();
            for (String s : tags) {
                if (s == null) continue;
                String tt = s.trim();
                if (!tt.isBlank()) cleaned.add(tt);
            }
            if (!cleaned.isEmpty()) tagsLine = "标签：" + String.join("、", cleaned) + "\n";
        }

        String out = safeTemplate;
        out = out.replace("{{count}}", String.valueOf(count));
        out = out.replace("{{boardLine}}", boardLine);
        out = out.replace("{{tagsLine}}", tagsLine);
        out = out.replace("{{content}}", content == null ? "" : content);

        out = out.replace("{{boardName}}", bn);
        out = out.replace("{{tags}}", (tagsLine.isBlank() ? "" : tagsLine.replace("标签：", "").trim()));
        return out;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

