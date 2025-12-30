package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestResponse;
import com.example.EnterpriseRagCommunity.service.ai.client.BailianOpenAiSseClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class AiPostTitleService {

    private static final int DEFAULT_COUNT = 5;
    private static final int MAX_COUNT = 10;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPostTitleService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public AiPostTitleSuggestResponse suggestTitles(AiPostTitleSuggestRequest req) {
        int count = req.getCount() == null ? DEFAULT_COUNT : req.getCount();
        if (count <= 0) count = DEFAULT_COUNT;
        if (count > MAX_COUNT) count = MAX_COUNT;

        String model = (req.getModel() != null && !req.getModel().isBlank()) ? req.getModel() : aiProperties.getModel();
        Double temperature = req.getTemperature();
        if (temperature == null) temperature = 0.4; // more stable for titles

        String content = req.getContent();
        // avoid huge prompt cost
        if (content.length() > 4000) {
            content = content.substring(0, 4000);
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请为下面这段社区帖子内容生成 ").append(count)
                .append(" 个中文标题候选。\n")
                .append("要求：\n")
                .append("- 每个标题不超过 30 个汉字\n")
                .append("- 风格适度多样（提问式/总结式/爆点式），但不要低俗\n")
                .append("- 标题之间不要重复\n")
                .append("- 只输出严格 JSON，不要输出任何解释文字\n")
                .append("- JSON 格式：{\"titles\":[\"...\",...]}\n");

        if (req.getBoardName() != null && !req.getBoardName().isBlank()) {
            userPrompt.append("版块：").append(req.getBoardName()).append("\n");
        }
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            userPrompt.append("标签：").append(String.join("、", req.getTags())).append("\n");
        }

        userPrompt.append("帖子内容：\n").append(content);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是专业的中文社区运营编辑，擅长给帖子拟标题。"));
        messages.add(Map.of("role", "user", "content", userPrompt.toString()));

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
}

