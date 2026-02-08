package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.entity.ai.PostAiSummaryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostAiSummaryRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPostSummaryService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PENDING = "PENDING";

    private final AiProperties aiProperties;
    private final PostSummaryGenConfigService postSummaryGenConfigService;
    private final PostsRepository postsRepository;
    private final PostAiSummaryRepository postAiSummaryRepository;
    private final LlmGateway llmGateway;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("aiExecutor")
    @Transactional
    public void generateForPostIdAsync(Long postId, Long actorUserId) {
        if (postId == null) return;
        PostsEntity post = postsRepository.findById(postId).orElse(null);
        if (post == null) return;

        PostSummaryGenConfigEntity cfg = postSummaryGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) return;

        int maxChars = cfg.getMaxContentChars() == null ? PostSummaryGenConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars <= 0) maxChars = PostSummaryGenConfigService.DEFAULT_MAX_CONTENT_CHARS;

        String modelOverride = (cfg.getModel() != null && !cfg.getModel().isBlank()) ? cfg.getModel() : null;
        Double temperature = cfg.getTemperature();

        String rawTitle = post.getTitle() == null ? "" : post.getTitle();
        String rawContent = post.getContent() == null ? "" : post.getContent();

        String clippedContent = rawContent.trim();
        if (clippedContent.length() > maxChars) {
            clippedContent = clippedContent.substring(0, maxChars);
        }

        String userPrompt = renderPrompt(cfg.getPromptTemplate(), rawTitle, clippedContent, extractTagsLine(post.getMetadata()));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是专业的中文社区内容摘要助手。只输出严格 JSON，不要输出解释文字。"));
        messages.add(ChatMessage.user(userPrompt));

        long started = System.currentTimeMillis();
        String rawJson;
        String usedProviderId = null;
        String usedModel = null;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(LlmQueueTaskType.SUMMARY_GEN, cfg.getProviderId(), modelOverride, messages, temperature);
            rawJson = routed == null ? null : routed.text();
            usedProviderId = routed == null ? null : routed.providerId();
            usedModel = routed == null ? null : routed.model();
        } catch (Exception e) {
            recordFailure(postId, actorUserId, cfg.getProviderId(), modelOverride, temperature, maxChars, cfg.getVersion(), System.currentTimeMillis() - started, e);
            return;
        }

        long latency = System.currentTimeMillis() - started;
        try {
            String assistantText = extractAssistantContent(rawJson);
            ParsedSummary parsed = parseSummaryFromAssistantText(assistantText);
            saveSuccess(postId, actorUserId, usedProviderId, usedModel, temperature, maxChars, cfg.getVersion(), latency, parsed);
        } catch (Exception e) {
            recordFailure(postId, actorUserId, usedProviderId, usedModel, temperature, maxChars, cfg.getVersion(), latency, e);
        }
    }

    private void saveSuccess(
            Long postId,
            Long actorUserId,
            String providerId,
            String model,
            Double temperature,
            int maxChars,
            Integer promptVersion,
            long latency,
            ParsedSummary parsed
    ) {
        LocalDateTime now = LocalDateTime.now();

        PostAiSummaryEntity s = postAiSummaryRepository.findByPostId(postId).orElseGet(PostAiSummaryEntity::new);
        s.setPostId(postId);
        s.setStatus(STATUS_SUCCESS);
        s.setSummaryTitle(cleanTitle(parsed.title));
        s.setSummaryText(cleanSummary(parsed.summary));
        s.setModel(model);
        s.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());
        s.setTemperature(temperature);
        s.setAppliedMaxContentChars(maxChars);
        s.setLatencyMs(latency);
        s.setGeneratedAt(now);
        s.setErrorMessage(null);
        s.setUpdatedAt(now);
        postAiSummaryRepository.save(s);

        PostSummaryGenHistoryEntity h = new PostSummaryGenHistoryEntity();
        h.setActorUserId(actorUserId);
        h.setPostId(postId);
        h.setStatus(STATUS_SUCCESS);
        h.setCreatedAt(now);
        h.setModel(model);
        h.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());
        h.setTemperature(temperature);
        h.setAppliedMaxContentChars(maxChars);
        h.setLatencyMs(latency);
        h.setPromptVersion(promptVersion);
        h.setErrorMessage(null);
        postSummaryGenConfigService.recordHistory(h);
    }

    private void recordFailure(
            Long postId,
            Long actorUserId,
            String providerId,
            String model,
            Double temperature,
            int maxChars,
            Integer promptVersion,
            long latency,
            Exception e
    ) {
        LocalDateTime now = LocalDateTime.now();

        String err = stackTraceToString(e);
        if (err.length() > 12000) err = err.substring(0, 12000);

        PostAiSummaryEntity s = postAiSummaryRepository.findByPostId(postId).orElseGet(PostAiSummaryEntity::new);
        s.setPostId(postId);
        s.setStatus(STATUS_FAILED);
        s.setSummaryTitle(null);
        s.setSummaryText(null);
        s.setModel(model);
        s.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());
        s.setTemperature(temperature);
        s.setAppliedMaxContentChars(maxChars);
        s.setLatencyMs(latency);
        s.setGeneratedAt(now);
        s.setErrorMessage(err);
        s.setUpdatedAt(now);
        postAiSummaryRepository.save(s);

        PostSummaryGenHistoryEntity h = new PostSummaryGenHistoryEntity();
        h.setActorUserId(actorUserId);
        h.setPostId(postId);
        h.setStatus(STATUS_FAILED);
        h.setCreatedAt(now);
        h.setModel(model);
        h.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());
        h.setTemperature(temperature);
        h.setAppliedMaxContentChars(maxChars);
        h.setLatencyMs(latency);
        h.setPromptVersion(promptVersion);
        h.setErrorMessage(err);
        postSummaryGenConfigService.recordHistory(h);
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

    ParsedSummary parseSummaryFromAssistantText(String assistantText) {
        if (assistantText == null) assistantText = "";
        String raw = assistantText.trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("AI 输出为空");
        }

        String t = raw;
        int lObj = t.indexOf('{');
        int rObj = t.lastIndexOf('}');
        if (lObj >= 0 && rObj > lObj) {
            t = t.substring(lObj, rObj + 1);
        } else {
            return new ParsedSummary(null, raw);
        }

        try {
            JsonNode root = objectMapper.readTree(t);
            String title = root.path("title").isTextual() ? root.path("title").asText() : null;
            String summary = root.path("summary").isTextual() ? root.path("summary").asText() : null;
            if (summary == null || summary.trim().isBlank()) {
                return new ParsedSummary(title, raw);
            }
            return new ParsedSummary(title, summary);
        } catch (Exception ignore) {
            return new ParsedSummary(null, raw);
        }
    }

    private static String renderPrompt(String template, String title, String content, String tagsLine) {
        String safeTemplate = (template == null || template.isBlank()) ? PostSummaryGenConfigService.DEFAULT_PROMPT_TEMPLATE : template;
        String out = safeTemplate;
        out = out.replace("{{title}}", title == null ? "" : title);
        out = out.replace("{{content}}", content == null ? "" : content);
        out = out.replace("{{tagsLine}}", tagsLine == null ? "" : tagsLine);
        return out;
    }

    private static String extractTagsLine(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return "";
        Object tags = metadata.get("tags");
        if (!(tags instanceof List<?> list) || list.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            if (!s.isBlank()) out.add(s);
        }
        if (out.isEmpty()) return "";
        return "标签：" + String.join("、", out);
    }

    private static String cleanTitle(String t) {
        if (t == null) return null;
        String s = t.trim();
        if (s.isEmpty()) return null;
        s = s.replaceAll("^[\"“”]+|[\"“”]+$", "");
        if (s.length() > 191) s = s.substring(0, 191);
        return s.trim();
    }

    private static String cleanSummary(String t) {
        if (t == null) return null;
        String s = t.trim();
        if (s.isEmpty()) return null;
        if (s.length() > 8000) s = s.substring(0, 8000);
        return s.trim();
    }

    private static String stackTraceToString(Throwable e) {
        if (e == null) return "";
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    record ParsedSummary(String title, String summary) {
    }
}
