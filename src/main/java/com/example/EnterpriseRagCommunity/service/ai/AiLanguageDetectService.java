package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiLanguageDetectService {

    private final SemanticTranslateConfigService semanticTranslateConfigService;
    private final LlmGateway llmGateway;
    private final PromptsRepository promptsRepository;
    private final PromptLlmParamResolver promptLlmParamResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROMPT_CODE = "LANG_DETECT";

    public List<String> detectLanguages(String content) {
        SemanticTranslateConfigEntity cfg = semanticTranslateConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("翻译功能已关闭");
        }

        String normalizedContent = normalizeContent(content, cfg);

        PromptsEntity prompt = promptsRepository.findByPromptCode(PROMPT_CODE)
                .orElseThrow(() -> new IllegalStateException("Prompt code not found: " + PROMPT_CODE));

        PromptLlmParams params = promptLlmParamResolver.resolveText(
            prompt,
            null,
            null,
            null,
            null,
            null,
            null,
            0.0,
            null
        );

        String userPrompt = renderPrompt(prompt.getUserPromptTemplate(), normalizedContent);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompt.getSystemPrompt()));
        messages.add(ChatMessage.user(userPrompt));

        String rawJson;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(
                    LlmQueueTaskType.UNKNOWN,
                        params.providerId(),
                        params.model(),
                    messages,
                        params.temperature(),
                        params.topP(),
                    null,
                    null,
                        params.enableThinking()
            );
            rawJson = routed == null ? null : routed.text();
        } catch (Exception e) {
            throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
        }

        String assistantText = extractAssistantContent(rawJson);
        return parseLanguagesFromAssistantText(assistantText);
    }

    private String extractAssistantContent(String rawJson) {
        return AiResponseParsingUtils.extractAssistantContent(objectMapper, rawJson);
    }

    private List<String> parseLanguagesFromAssistantText(String assistantText) {
        if (assistantText == null) assistantText = "";
        String json = assistantText.trim();
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

        return AiResponseParsingUtils.deduplicateAndLimit(out, 3);
    }

    private static String cleanLang(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replaceAll("^[\"“”]+|[\"“”]+$", "");
        t = t.replaceAll("\\s+", "");
        if (t.length() > 16) t = t.substring(0, 16);
        return t.trim();
    }

    private static String normalizeContent(String content, SemanticTranslateConfigEntity cfg) {
        String normalizedContent = content == null ? "" : content.trim();
        int maxChars = cfg.getMaxContentChars() == null
                ? SemanticTranslateConfigService.DEFAULT_MAX_CONTENT_CHARS
                : cfg.getMaxContentChars();
        if (maxChars > 0 && normalizedContent.length() > maxChars) {
            normalizedContent = normalizedContent.substring(0, maxChars);
        }
        return normalizedContent;
    }

    private static String renderPrompt(String template, String content) {
        String safeTemplate = (template == null || template.isBlank())
                ? "{{content}}"
                : template;
        return safeTemplate.replace("{{content}}", content == null ? "" : content.trim());
    }
}
