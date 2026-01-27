package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.entity.ai.PostRiskTagGenConfigEntity;
import com.example.EnterpriseRagCommunity.service.ai.client.BailianOpenAiSseClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPostRiskTagService {

    private final AiProperties aiProperties;
    private final PostRiskTagGenConfigService postRiskTagGenConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> suggestRiskTags(String title, String content) {
        PostRiskTagGenConfigEntity cfg = postRiskTagGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            return List.of();
        }

        int maxCount = cfg.getMaxCount() == null ? PostRiskTagGenConfigService.DEFAULT_MAX_COUNT : cfg.getMaxCount();
        if (maxCount <= 0) maxCount = PostRiskTagGenConfigService.DEFAULT_MAX_COUNT;
        if (maxCount > 50) maxCount = 50;

        String model = (cfg.getModel() != null && !cfg.getModel().isBlank()) ? cfg.getModel() : aiProperties.getModel();
        Double temperature = cfg.getTemperature();
        if (temperature == null) temperature = 0.2;

        String t = title == null ? "" : title.trim();
        String c = content == null ? "" : content.trim();

        int maxChars = cfg.getMaxContentChars() == null ? PostRiskTagGenConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && c.length() > maxChars) {
            c = c.substring(0, maxChars);
        }

        String sys = cfg.getSystemPrompt() == null ? "" : cfg.getSystemPrompt();
        sys = sys.replace("{{maxCount}}", String.valueOf(maxCount));

        String userPrompt = renderPrompt(cfg.getPromptTemplate(), t, c);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", sys));
        messages.add(Map.of("role", "user", "content", userPrompt));

        String rawJson;
        try {
            rawJson = new BailianOpenAiSseClient(aiProperties)
                    .chatCompletionsOnce(null, aiProperties.getBaseUrl(), model, messages, temperature);
        } catch (Exception e) {
            throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
        }

        String assistantText = extractAssistantContent(rawJson);
        return parseRiskTagsFromAssistantText(assistantText, maxCount);
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

    private List<String> parseRiskTagsFromAssistantText(String assistantText, int maxCount) {
        if (assistantText == null) assistantText = "";
        String json = assistantText.trim();

        int l = json.indexOf('{');
        int r = json.lastIndexOf('}');
        if (l >= 0 && r > l) {
            json = json.substring(l, r + 1);
        }

        List<String> tags = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("riskTags");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    if (!n.isTextual()) continue;
                    String t = cleanTag(n.asText());
                    if (!t.isBlank()) tags.add(t);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 输出无法解析为 riskTags，请重试", e);
        }

        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : tags) {
            if (t.isBlank()) continue;
            set.add(t);
        }

        List<String> out = new ArrayList<>(set);
        if (out.size() > maxCount) out = out.subList(0, maxCount);
        return out;
    }

    private static String cleanTag(String t) {
        if (t == null) return "";
        t = t.trim();
        t = t.replaceAll("^[\"“”]+|[\"“”]+$", "");
        if (t.length() > 64) t = t.substring(0, 64);
        return t.trim();
    }

    private static String renderPrompt(String template, String title, String content) {
        String safeTemplate = (template == null || template.isBlank())
                ? PostRiskTagGenConfigService.DEFAULT_PROMPT_TEMPLATE
                : template;

        String tt = title == null ? "" : title.trim();

        String out = safeTemplate;
        out = out.replace("{{title}}", tt);
        out = out.replace("{{content}}", content == null ? "" : content);
        return out;
    }
}
