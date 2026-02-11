package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostLangLabelGenConfigEntity;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPostLangLabelService {

    private final AiProperties aiProperties;
    private final PostLangLabelGenConfigService postLangLabelGenConfigService;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPostLangLabelSuggestResponse suggestLanguages(AiPostLangLabelSuggestRequest req) {
        PostLangLabelGenConfigEntity cfg = postLangLabelGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("语言标签生成已关闭");
        }

        String modelOverride = (cfg.getModel() != null && !cfg.getModel().isBlank()) ? cfg.getModel() : null;
        Double temperature = cfg.getTemperature();
        if (temperature == null) temperature = 0.0;
        Double topP = cfg.getTopP();
        if (topP == null) topP = 0.2;

        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        String content = req.getContent() == null ? "" : req.getContent().trim();

        int maxChars = cfg.getMaxContentChars() == null ? PostLangLabelGenConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars);
        }

        String userPrompt = renderPrompt(cfg.getPromptTemplate(), title, content);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(cfg.getSystemPrompt()));
        messages.add(ChatMessage.user(userPrompt));

        long started = System.currentTimeMillis();
        String rawJson;
        String usedModel;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(
                    LlmQueueTaskType.LANGUAGE_TAG_GEN,
                    cfg.getProviderId(),
                    modelOverride,
                    messages,
                    temperature,
                    topP,
                    null,
                    null,
                    cfg.getEnableThinking()
            );
            rawJson = routed == null ? null : routed.text();
            usedModel = routed == null ? null : routed.model();
        } catch (Exception e) {
            throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
        }
        long latency = System.currentTimeMillis() - started;

        String assistantText = extractAssistantContent(rawJson);
        List<String> langs = parseLanguagesFromAssistantText(assistantText);
        return new AiPostLangLabelSuggestResponse(langs, usedModel, latency);
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
        assistantText = assistantText.trim();

        String json = assistantText;
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
        if (t.length() > 16) t = t.substring(0, 16);
        return t.trim();
    }

    private static String renderPrompt(String template, String title, String content) {
        String safeTemplate = (template == null || template.isBlank())
                ? PostLangLabelGenConfigService.DEFAULT_PROMPT_TEMPLATE
                : template;
        String out = safeTemplate;
        out = out.replace("{{title}}", title == null ? "" : title.trim());
        out = out.replace("{{content}}", content == null ? "" : content.trim());
        return out;
    }
}
