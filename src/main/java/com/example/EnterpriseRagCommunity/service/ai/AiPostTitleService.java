package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.entity.semantic.GenerationJobsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobType;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationTargetType;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPostTitleService {

    private final PostTitleGenConfigService postTitleGenConfigService;
    private final LlmGateway llmGateway;
    private final PromptsRepository promptsRepository;
    private final GenerationJobsRepository generationJobsRepository;
    private final PromptLlmParamResolver promptLlmParamResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPostTitleSuggestResponse suggestTitles(AiPostTitleSuggestRequest req, Long actorUserId) {
        PostSuggestionGenConfigEntity cfg = postTitleGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("标题生成已关闭");
        }

        int defaultCount = cfg.getDefaultCount() == null ? PostTitleGenConfigService.DEFAULT_DEFAULT_COUNT : cfg.getDefaultCount();
        int maxCount = cfg.getMaxCount() == null ? PostTitleGenConfigService.DEFAULT_MAX_COUNT : cfg.getMaxCount();

        int count = req.getCount() == null ? defaultCount : req.getCount();
        if (count <= 0) count = defaultCount;
        if (count > maxCount) count = maxCount;

        String content = req.getContent() == null ? "" : req.getContent();
        content = content.trim();
        int contentLen = content.length();

        int maxChars = cfg.getMaxContentChars() == null ? PostTitleGenConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars);
        }

        String promptCode = cfg.getPromptCode();
        if (promptCode == null || promptCode.isBlank()) {
            promptCode = PostTitleGenConfigService.DEFAULT_PROMPT_CODE;
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
            0.9
        );

        String modelOverride = req.getModel() != null && !req.getModel().isBlank()
            ? req.getModel().trim()
            : params.model();

        Double temperature = req.getTemperature() != null ? req.getTemperature() : params.temperature();
        if (temperature == null) temperature = 0.4;
        if (temperature < 0 || temperature > 2) throw new IllegalArgumentException("temperature 需在 [0,2] 范围内");

        Double topP = req.getTopP() != null ? req.getTopP() : params.topP();
        if (topP == null) topP = 0.9;
        if (topP < 0 || topP > 1) throw new IllegalArgumentException("topP 需在 [0,1] 范围内");

        String userPrompt = renderPrompt(prompt.getUserPromptTemplate(), count, req.getBoardName(), req.getTags(), content);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompt.getSystemPrompt()));
        messages.add(ChatMessage.user(userPrompt));

        long started = System.currentTimeMillis();
        String rawJson;
        String usedProviderId;
        String usedModel;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(
                    LlmQueueTaskType.TITLE_GEN,
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
        List<String> titles = parseTitlesFromAssistantText(assistantText, count);

        long latency = System.currentTimeMillis() - started;

        if (Boolean.TRUE.equals(cfg.getHistoryEnabled()) && actorUserId != null) {
            GenerationJobsEntity job = new GenerationJobsEntity();
            job.setJobType(GenerationJobType.SUGGESTION);
            job.setTargetType(GenerationTargetType.POST);
            job.setTargetId(0L);
            job.setStatus(GenerationJobStatus.SUCCEEDED);
            job.setPromptCode(promptCode);
            job.setModel(usedModel);
            job.setProviderId(usedProviderId);
            job.setTemperature(temperature);
            job.setTopP(topP);
            job.setLatencyMs(latency);
            job.setPromptVersion(cfg.getVersion());
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(job.getCreatedAt());
            generationJobsRepository.save(job);

            PostSuggestionGenHistoryEntity h = new PostSuggestionGenHistoryEntity();
            h.setKind(SuggestionKind.TITLE);
            h.setUserId(actorUserId);
            h.setCreatedAt(LocalDateTime.now());
            h.setBoardName(blankToNull(req.getBoardName()));
            h.setInputTagsJson(req.getTags() == null ? List.of() : new ArrayList<>(req.getTags()));
            h.setRequestedCount(count);
            h.setAppliedMaxContentChars(maxChars);
            h.setContentLen(contentLen);
            h.setContentExcerpt(buildExcerpt(req.getContent()));
            h.setOutputJson(new ArrayList<>(titles));
            h.setJobId(job.getId());
            postTitleGenConfigService.recordHistory(h);
        }

        return new AiPostTitleSuggestResponse(titles, usedModel, latency);
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

    List<String> parseTitlesFromAssistantText(String assistantText, int expectedCount) {
        if (assistantText == null) assistantText = "";
        assistantText = assistantText.trim();

        String json = assistantText;
        int lObj = json.indexOf('{');
        int rObj = json.lastIndexOf('}');
        int lArr = json.indexOf('[');
        int rArr = json.lastIndexOf(']');
        if (lObj >= 0 && rObj > lObj && (lArr < 0 || lObj < lArr)) {
            json = json.substring(lObj, rObj + 1);
        } else if (lArr >= 0 && rArr > lArr) {
            json = json.substring(lArr, rArr + 1);
        }

        List<String> titles = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.isArray() ? root : root.path("titles");
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
        set.addAll(titles);

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
                ? ""
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

        out = out.replace("{{boardName}}", bn);
        out = out.replace("{{tags}}", (tagsLine.isBlank() ? "" : tagsLine.replace("标签：", "").trim()));
        out = out.replace("{{content}}", content == null ? "" : content);
        return out;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
