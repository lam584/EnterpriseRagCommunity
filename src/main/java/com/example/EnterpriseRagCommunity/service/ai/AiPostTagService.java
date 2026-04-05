package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.entity.semantic.GenerationJobsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPostTagService {

    private final PostTagGenConfigService postTagGenConfigService;
    private final LlmGateway llmGateway;
    private final PromptsRepository promptsRepository;
    private final GenerationJobsRepository generationJobsRepository;
    private final PromptLlmParamResolver promptLlmParamResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPostTagSuggestResponse suggestTags(AiPostTagSuggestRequest req, Long actorUserId) {
        PostSuggestionGenConfigEntity cfg = postTagGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("主题标签生成已关闭");
        }

        int defaultCount = cfg.getDefaultCount() == null ? PostTagGenConfigService.DEFAULT_DEFAULT_COUNT : cfg.getDefaultCount();
        int maxCount = cfg.getMaxCount() == null ? PostTagGenConfigService.DEFAULT_MAX_COUNT : cfg.getMaxCount();

        int count = resolveRequestedCount(req.getCount(), defaultCount, maxCount);

        String content = req.getContent() == null ? "" : req.getContent();
        content = content.trim();
        int contentLen = content.length();

        int maxChars = cfg.getMaxContentChars() == null ? PostTagGenConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars);
        }

        String promptCode = cfg.getPromptCode();
        if (promptCode == null || promptCode.isBlank()) {
            promptCode = PostTagGenConfigService.DEFAULT_PROMPT_CODE;
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
            0.4,
            0.8
        );

        AiPromptSamplingSupport.PromptSampling sampling = AiPromptSamplingSupport.resolve(
                req.getModel(), params.model(),
                req.getTemperature(), params.temperature(), 0.4,
                req.getTopP(), params.topP(), 0.8
        );
        String modelOverride = sampling.modelOverride();
        Double temperature = sampling.temperature();
        Double topP = sampling.topP();

        String userPrompt = renderPrompt(prompt.getUserPromptTemplate(), count, req.getBoardName(), req.getTitle(), req.getTags(), content);

        List<ChatMessage> messages = ChatMessageSupport.buildSystemUserMessages(prompt.getSystemPrompt(), userPrompt);

        long started = System.currentTimeMillis();
        String rawJson;
        String usedProviderId;
        String usedModel;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(
                    LlmQueueTaskType.TOPIC_TAG_GEN,
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
            usedProviderId = routed == null ? null : routed.providerId();
            usedModel = routed == null ? null : routed.model();
        } catch (Exception e) {
            throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
        }

        String assistantText = extractAssistantContent(rawJson);
        List<String> tags = parseTagsFromAssistantText(assistantText, count);

        long latency = System.currentTimeMillis() - started;

        if (Boolean.TRUE.equals(cfg.getHistoryEnabled()) && actorUserId != null) {
            GenerationJobsEntity job = AiSuggestionSupport.buildSucceededSuggestionJob(
                    promptCode,
                    usedModel,
                    usedProviderId,
                    temperature,
                    topP,
                    latency,
                    cfg.getVersion()
            );
            generationJobsRepository.save(job);

            PostSuggestionGenHistoryEntity h = AiSuggestionSupport.newSuggestionHistory(SuggestionKind.TOPIC_TAG, actorUserId);
            h.setBoardName(blankToNull(req.getBoardName()));
            h.setTitleExcerpt(buildTitleExcerpt(req.getTitle()));
            applyCommonHistoryFields(h, count, maxChars, contentLen, req.getContent(), tags, job.getId());
            postTagGenConfigService.recordHistory(h);
        }

        return new AiPostTagSuggestResponse(tags, usedModel, latency);
    }

    private String extractAssistantContent(String rawJson) {
        return AiResponseParsingUtils.extractAssistantContent(objectMapper, rawJson);
    }

    List<String> parseTagsFromAssistantText(String assistantText, int expectedCount) {
        if (expectedCount < 0) {
            throw new IndexOutOfBoundsException("expectedCount must be >= 0");
        }
        if (assistantText == null) return List.of();
        assistantText = assistantText.trim();
        if (assistantText.isEmpty()) return List.of();

        String json = extractJsonPayload(assistantText);

        List<String> tags = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.isArray() ? root : root.path("tags");
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

        return AiResponseParsingUtils.deduplicateAndLimit(tags, expectedCount);
    }

    private String cleanTag(String t) {
        if (t == null) return "";
        t = t.trim();
        t = t.replaceAll("^[\"“”]+|[\"“”]+$", "");
        if (t.length() > 20) t = t.substring(0, 20);
        return t.trim();
    }

    private static String extractJsonPayload(String assistantText) {
        int lObj = assistantText.indexOf('{');
        int rObj = assistantText.lastIndexOf('}');
        int lArr = assistantText.indexOf('[');
        int rArr = assistantText.lastIndexOf(']');
        if ((lObj >= 0 && rObj <= lObj) || (lArr >= 0 && rArr <= lArr)) {
            throw new IllegalArgumentException("AI 输出包含不完整的 JSON 片段，请重试");
        }
        if (lObj >= 0 && (lArr < 0 || lObj < lArr)) {
            return assistantText.substring(lObj, rObj + 1);
        }
        if (lArr >= 0) {
            return assistantText.substring(lArr, rArr + 1);
        }
        return assistantText;
    }

    private static String buildExcerpt(String content) {
        if (content == null) return null;
        String t = content.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 240) t = t.substring(0, 240);
        return t;
    }

    private static void applyCommonHistoryFields(PostSuggestionGenHistoryEntity history,
                                                 int requestedCount,
                                                 int maxChars,
                                                 int contentLen,
                                                 String content,
                                                 List<String> output,
                                                 Long jobId) {
        history.setRequestedCount(requestedCount);
        history.setAppliedMaxContentChars(maxChars);
        history.setContentLen(contentLen);
        history.setContentExcerpt(buildExcerpt(content));
        history.setOutputJson(output == null ? List.of() : new ArrayList<>(output));
        history.setJobId(jobId);
    }

    private static int resolveRequestedCount(Integer requestedCount, int defaultCount, int maxCount) {
        int count = requestedCount == null ? defaultCount : requestedCount;
        if (count <= 0) count = defaultCount;
        if (count > maxCount) count = maxCount;
        return count;
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
                ? ""
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
        out = out.replace("{{boardName}}", bn);
        out = out.replace("{{title}}", tt);
        out = out.replace("{{content}}", content == null ? "" : content);
        return out;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
