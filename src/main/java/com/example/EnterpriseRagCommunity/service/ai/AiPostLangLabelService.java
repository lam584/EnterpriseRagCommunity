package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostLangLabelGenConfigEntity;
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
public class AiPostLangLabelService {

    private final PostLangLabelGenConfigService postLangLabelGenConfigService;
    private final LlmGateway llmGateway;
    private final PromptsRepository promptsRepository;
    private final PromptLlmParamResolver promptLlmParamResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPostLangLabelSuggestResponse suggestLanguages(AiPostLangLabelSuggestRequest req) {
        PostLangLabelGenConfigEntity cfg = postLangLabelGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("语言标签生成已关闭");
        }

        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        String content = req.getContent() == null ? "" : req.getContent().trim();

        int maxChars = cfg.getMaxContentChars() == null ? PostLangLabelGenConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars);
        }

        String promptCode = cfg.getPromptCode();
        if (promptCode == null || promptCode.isBlank()) {
            promptCode = PostLangLabelGenConfigService.DEFAULT_PROMPT_CODE;
        }

        PromptsEntity prompt = promptsRepository.findByPromptCode(promptCode)
                .orElseThrow(() -> new IllegalStateException("Prompt code not found: " + cfg.getPromptCode()));

        PromptLlmParams params = promptLlmParamResolver.resolveText(
            prompt,
            null,
            null,
            null,
            null,
            null,
            null,
            0.0,
            0.2
        );

        String modelOverride = params.model();
        Double temperature = params.temperature();
        Double topP = params.topP();

        String userPrompt = renderPrompt(prompt.getUserPromptTemplate(), title, content);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompt.getSystemPrompt()));
        messages.add(ChatMessage.user(userPrompt));

        long started = System.currentTimeMillis();
        String rawJson;
        String usedModel;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(
                    LlmQueueTaskType.LANGUAGE_TAG_GEN,
                    params.providerId(),
                    modelOverride,
                    messages,
                    temperature,
                    topP,
                    null,
                    null,
                        params.enableThinking()
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
        String out = (template == null || template.isBlank())
                ? ""
                : template;
        out = out.replace("{{title}}", title == null ? "" : title.trim());
        out = out.replace("{{content}}", content == null ? "" : content.trim());
        return out;
    }
}
