package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostTagGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostTagGenHistoryEntity;
import com.example.EnterpriseRagCommunity.service.ai.client.BailianOpenAiSseClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPostTagService {

    private final AiProperties aiProperties;
    private final PostTagGenConfigService postTagGenConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPostTagSuggestResponse suggestTags(AiPostTagSuggestRequest req, Long actorUserId) {
        PostTagGenConfigEntity cfg = postTagGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("主题标签生成已关闭");
        }

        int defaultCount = cfg.getDefaultCount() == null ? PostTagGenConfigService.DEFAULT_DEFAULT_COUNT : cfg.getDefaultCount();
        int maxCount = cfg.getMaxCount() == null ? PostTagGenConfigService.DEFAULT_MAX_COUNT : cfg.getMaxCount();

        int count = req.getCount() == null ? defaultCount : req.getCount();
        if (count <= 0) count = defaultCount;
        if (count > maxCount) count = maxCount;

        String model = (cfg.getModel() != null && !cfg.getModel().isBlank()) ? cfg.getModel() : aiProperties.getModel();
        Double temperature = cfg.getTemperature();
        if (temperature == null) temperature = 0.4;

        String content = req.getContent() == null ? "" : req.getContent();
        content = content.trim();
        int contentLen = content.length();

        int maxChars = cfg.getMaxContentChars() == null ? PostTagGenConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars);
        }

        String userPrompt = renderPrompt(cfg.getPromptTemplate(), count, req.getBoardName(), req.getTitle(), req.getTags(), content);

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
        List<String> tags = parseTagsFromAssistantText(assistantText, count);

        long latency = System.currentTimeMillis() - started;

        if (Boolean.TRUE.equals(cfg.getHistoryEnabled()) && actorUserId != null) {
            PostTagGenHistoryEntity h = new PostTagGenHistoryEntity();
            h.setUserId(actorUserId);
            h.setCreatedAt(LocalDateTime.now());
            h.setBoardName(blankToNull(req.getBoardName()));
            h.setTitleExcerpt(buildTitleExcerpt(req.getTitle()));
            h.setRequestedCount(count);
            h.setAppliedMaxContentChars(maxChars);
            h.setContentLen(contentLen);
            h.setContentExcerpt(buildExcerpt(req.getContent()));
            h.setTagsJson(new ArrayList<>(tags));
            h.setModel(model);
            h.setTemperature(temperature);
            h.setLatencyMs(latency);
            h.setPromptVersion(cfg.getVersion());
            postTagGenConfigService.recordHistory(h);
        }

        return new AiPostTagSuggestResponse(tags, model, latency);
    }

    private String extractAssistantContent(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode contentNode = first.path("message").path("content");
                if (!contentNode.isMissingNode() && contentNode.isTextual()) {
                    return contentNode.asText();
                }
                JsonNode textNode = first.path("text");
                if (!textNode.isMissingNode() && textNode.isTextual()) {
                    return textNode.asText();
                }
            }
        } catch (Exception ignore) {
        }
        return rawJson;
    }

    private List<String> parseTagsFromAssistantText(String assistantText, int expectedCount) {
        if (assistantText == null) assistantText = "";
        assistantText = assistantText.trim();

        String json = assistantText;
        int l = json.indexOf('{');
        int r = json.lastIndexOf('}');
        if (l >= 0 && r > l) {
            json = json.substring(l, r + 1);
        }

        List<String> tags = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("tags");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    if (!n.isTextual()) continue;
                    String t = cleanTag(n.asText());
                    if (!t.isBlank()) tags.add(t);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 输出无法解析为标签列表，请重试", e);
        }

        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : tags) {
            if (t.isBlank()) continue;
            set.add(t);
        }

        List<String> out = new ArrayList<>(set);
        if (out.size() > expectedCount) {
            out = out.subList(0, expectedCount);
        }
        return out;
    }

    private String cleanTag(String t) {
        if (t == null) return "";
        t = t.trim();
        t = t.replaceAll("^[\"“”]+|[\"“”]+$", "");
        if (t.length() > 20) t = t.substring(0, 20);
        return t.trim();
    }

    private static String buildExcerpt(String content) {
        if (content == null) return null;
        String t = content.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 240) t = t.substring(0, 240);
        return t;
    }

    private static String buildTitleExcerpt(String title) {
        if (title == null) return null;
        String t = title.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 120) t = t.substring(0, 120);
        return t;
    }

    private static String renderPrompt(String template, int count, String boardName, String title, List<String> tags, String content) {
        String safeTemplate = (template == null || template.isBlank())
                ? PostTagGenConfigService.DEFAULT_PROMPT_TEMPLATE
                : template;

        String bn = boardName == null ? "" : boardName.trim();
        String boardLine = bn.isBlank() ? "" : ("版块：" + bn + "\n");

        String tt = title == null ? "" : title.trim();
        String titleLine = tt.isBlank() ? "" : ("标题：" + tt + "\n");

        String tagsLine = "";
        if (tags != null && !tags.isEmpty()) {
            List<String> cleaned = new ArrayList<>();
            for (String s : tags) {
                if (s == null) continue;
                String x = s.trim();
                if (!x.isBlank()) cleaned.add(x);
            }
            if (!cleaned.isEmpty()) tagsLine = "已有标签：" + String.join("、", cleaned) + "\n";
        }

        String out = safeTemplate;
        out = out.replace("{{count}}", String.valueOf(count));
        out = out.replace("{{boardLine}}", boardLine);
        out = out.replace("{{titleLine}}", titleLine);
        out = out.replace("{{tagsLine}}", tagsLine);
        out = out.replace("{{content}}", content == null ? "" : content);

        out = out.replace("{{boardName}}", bn);
        out = out.replace("{{title}}", tt);
        return out;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

