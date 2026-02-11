package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiLanguageDetectService {

    public static final String DEFAULT_SYSTEM_PROMPT = """
你是一个语言识别助手。
任务：根据输入文本，判断文本包含的自然语言。
输出要求：
1. 只输出 JSON（不要包裹 ```），格式：{"languages":["zh-CN","en-US"]}
2. languages 使用 BCP-47 风格语言标签（例如 zh-CN、en-US、ja-JP）。如果无法确定地区，可用基础语言码（zh/en/ja/...）。
3. 如果文本明显由多种语言混合组成，请输出多个语言标签（最多 3 个）。
4. 不要输出解释、不要输出多余字段。
""";

    public static final String DEFAULT_USER_PROMPT_TEMPLATE = """
文本：
{{content}}
""";

    private final AiProperties aiProperties;
    private final SemanticTranslateConfigService semanticTranslateConfigService;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> detectLanguages(String content) {
        SemanticTranslateConfigEntity cfg = semanticTranslateConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("翻译功能已关闭");
        }

        String normalizedContent = content == null ? "" : content.trim();
        int maxChars = cfg.getMaxContentChars() == null ? SemanticTranslateConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && normalizedContent.length() > maxChars) {
            normalizedContent = normalizedContent.substring(0, maxChars);
        }

        String modelOverride = (cfg.getModel() != null && !cfg.getModel().isBlank()) ? cfg.getModel() : null;
        Double temperature = cfg.getTemperature();
        if (temperature == null) temperature = 0.0;

        String userPrompt = renderPrompt(DEFAULT_USER_PROMPT_TEMPLATE, normalizedContent);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(DEFAULT_SYSTEM_PROMPT));
        messages.add(ChatMessage.user(userPrompt));

        String rawJson;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(
                    LlmQueueTaskType.UNKNOWN,
                    cfg.getProviderId(),
                    modelOverride,
                    messages,
                    temperature,
                    null,
                    null,
                    null,
                    cfg.getEnableThinking()
            );
            rawJson = routed == null ? null : routed.text();
        } catch (Exception e) {
            throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
        }

        String assistantText = extractAssistantContent(rawJson);
        return parseLanguagesFromAssistantText(assistantText);
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

    private List<String> parseLanguagesFromAssistantText(String assistantText) {
        if (assistantText == null) assistantText = "";
        String t = assistantText.trim();

        String json = t;
        int l = json.indexOf('{');
        int r = json.lastIndexOf('}');
        if (l >= 0 && r > l) {
            json = json.substring(l, r + 1);
        }

        List<String> out = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("languages");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    if (!n.isTextual()) continue;
                    String s = cleanLang(n.asText());
                    if (!s.isBlank()) out.add(s);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 输出无法解析为语言标签，请重试", e);
        }

        LinkedHashSet<String> set = new LinkedHashSet<>(out);
        List<String> langs = new ArrayList<>(set);
        if (langs.size() > 3) langs = langs.subList(0, 3);
        return langs;
    }

    private static String cleanLang(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replaceAll("^[\"“”]+|[\"“”]+$", "");
        t = t.replaceAll("\\s+", "");
        if (t.length() > 16) t = t.substring(0, 16);
        return t.trim();
    }

    private static String renderPrompt(String template, String content) {
        String safeTemplate = (template == null || template.isBlank())
                ? DEFAULT_USER_PROMPT_TEMPLATE
                : template;
        return safeTemplate.replace("{{content}}", content == null ? "" : content.trim());
    }
}
