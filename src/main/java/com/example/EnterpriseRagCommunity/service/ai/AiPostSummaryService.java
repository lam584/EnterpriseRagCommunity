package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostAiSummaryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.GenerationJobsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobType;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationTargetType;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostAiSummaryRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
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

    private final PostSummaryGenConfigService postSummaryGenConfigService;
    private final PostsRepository postsRepository;
    private final PostAiSummaryRepository postAiSummaryRepository;
    private final LlmGateway llmGateway;
    private final PromptsRepository promptsRepository;
    private final GenerationJobsRepository generationJobsRepository;
    private final PromptLlmParamResolver promptLlmParamResolver;

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

        String rawTitle = post.getTitle() == null ? "" : post.getTitle();
        String rawContent = post.getContent() == null ? "" : post.getContent();

        String clippedContent = rawContent.trim();
        if (clippedContent.length() > maxChars) {
            clippedContent = clippedContent.substring(0, maxChars);
        }

        String promptCode = cfg.getPromptCode();
        if (promptCode == null || promptCode.isBlank()) {
            promptCode = PostSummaryGenConfigService.DEFAULT_PROMPT_CODE;
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
            null,
            0.7
        );

        String modelOverride = params.model();
        Double temperature = params.temperature();
        Double topP = params.topP();

        String userPrompt = renderPrompt(prompt.getUserPromptTemplate(), rawTitle, clippedContent, extractTagsLine(post.getMetadata()));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompt.getSystemPrompt()));
        messages.add(ChatMessage.user(userPrompt));

        long started = System.currentTimeMillis();
        String rawJson;
        String usedProviderId;
        String usedModel;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(
                    LlmQueueTaskType.SUMMARY_GEN,
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
            recordFailure(postId, actorUserId, params.providerId(), modelOverride, temperature, topP, maxChars, cfg.getVersion(), System.currentTimeMillis() - started, e);
            return;
        }

        long latency = System.currentTimeMillis() - started;
        try {
            String assistantText = extractAssistantContent(rawJson);
            ParsedSummary parsed = parseSummaryFromAssistantText(assistantText);
            saveSuccess(postId, actorUserId, usedProviderId, usedModel, temperature, topP, maxChars, cfg.getVersion(), latency, parsed);
        } catch (Exception e) {
            recordFailure(postId, actorUserId, usedProviderId, usedModel, temperature, topP, maxChars, cfg.getVersion(), latency, e);
        }
    }

    private void saveSuccess(
            Long postId,
            Long actorUserId,
            String providerId,
            String model,
            Double temperature,
            Double topP,
            int maxChars,
            Integer promptVersion,
            long latency,
            ParsedSummary parsed
    ) {
        LocalDateTime now = LocalDateTime.now();

        GenerationJobsEntity job = new GenerationJobsEntity();
        job.setJobType(GenerationJobType.SUMMARY);
        job.setTargetType(GenerationTargetType.POST);
        job.setTargetId(postId);
        job.setStatus(GenerationJobStatus.SUCCEEDED);
        job.setModel(model);
        job.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());
        job.setTemperature(temperature);
        job.setTopP(topP);
        job.setLatencyMs(latency);
        job.setPromptVersion(promptVersion);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        generationJobsRepository.save(job);

        PostAiSummaryEntity s = postAiSummaryRepository.findByPostId(postId).orElseGet(PostAiSummaryEntity::new);
        s.setPostId(postId);
        s.setStatus(STATUS_SUCCESS);
        s.setSummaryTitle(cleanTitle(parsed.title));
        s.setSummaryText(cleanSummary(parsed.summary));
        s.setAppliedMaxContentChars(maxChars);
        s.setGeneratedAt(now);
        s.setErrorMessage(null);
        s.setJobId(job.getId());
        s.setUpdatedAt(now);
        postAiSummaryRepository.save(s);

        PostSummaryGenHistoryEntity h = new PostSummaryGenHistoryEntity();
        h.setActorUserId(actorUserId);
        h.setPostId(postId);
        h.setStatus(STATUS_SUCCESS);
        h.setCreatedAt(now);
        h.setAppliedMaxContentChars(maxChars);
        h.setErrorMessage(null);
        h.setJobId(job.getId());
        postSummaryGenConfigService.recordHistory(h);
    }

    private void recordFailure(
            Long postId,
            Long actorUserId,
            String providerId,
            String model,
            Double temperature,
            Double topP,
            int maxChars,
            Integer promptVersion,
            long latency,
            Exception e
    ) {
        LocalDateTime now = LocalDateTime.now();

        String err = stackTraceToString(e);
        if (err.length() > 12000) err = err.substring(0, 12000);

        GenerationJobsEntity job = new GenerationJobsEntity();
        job.setJobType(GenerationJobType.SUMMARY);
        job.setTargetType(GenerationTargetType.POST);
        job.setTargetId(postId);
        job.setStatus(GenerationJobStatus.FAILED);
        job.setModel(model);
        job.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());
        job.setTemperature(temperature);
        job.setTopP(topP);
        job.setLatencyMs(latency);
        job.setPromptVersion(promptVersion);
        job.setErrorMessage(err.length() > 255 ? err.substring(0, 255) : err);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        generationJobsRepository.save(job);

        PostAiSummaryEntity s = postAiSummaryRepository.findByPostId(postId).orElseGet(PostAiSummaryEntity::new);
        s.setPostId(postId);
        s.setStatus(STATUS_FAILED);
        s.setSummaryTitle(null);
        s.setSummaryText(null);
        s.setAppliedMaxContentChars(maxChars);
        s.setGeneratedAt(now);
        s.setErrorMessage(err);
        s.setJobId(job.getId());
        s.setUpdatedAt(now);
        postAiSummaryRepository.save(s);

        PostSummaryGenHistoryEntity h = new PostSummaryGenHistoryEntity();
        h.setActorUserId(actorUserId);
        h.setPostId(postId);
        h.setStatus(STATUS_FAILED);
        h.setCreatedAt(now);
        h.setAppliedMaxContentChars(maxChars);
        h.setErrorMessage(err);
        h.setJobId(job.getId());
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
        String content = extractJsonStringFieldOnePass(rawJson, "content");
        if (content != null && !content.isBlank()) return content;
        String text = extractJsonStringFieldOnePass(rawJson, "text");
        if (text != null && !text.isBlank()) return text;
        return rawJson;
    }

    private String extractJsonStringFieldOnePass(String json, String field) {
        if (json == null || field == null || field.isBlank()) return null;
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;

        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null;

        int start = i + 1;
        int j = start;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '"') {
                int slashCount = 0;
                int k = j - 1;
                while (k >= start && json.charAt(k) == '\\') {
                    slashCount++;
                    k--;
                }
                if ((slashCount & 1) == 0) break;
            }
            j++;
        }
        if (j >= json.length()) return null;

        String rawValue = json.substring(start, j);
        try {
            return objectMapper.readValue("\"" + rawValue + "\"", String.class);
        } catch (Exception ignore) {
            return rawValue;
        }
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

        JsonNode root = parseJsonObjectLenient(t);
        if (root == null) {
            String relaxedTitle = decodeEscapedContent(extractBetween(t, "\"title\":\"", "\",\"summary\""));
            String relaxedSummary = decodeEscapedContent(extractBetween(t, "\"summary\":\"", "\"}"));
            if (relaxedSummary == null || relaxedSummary.trim().isBlank()) {
                return new ParsedSummary(null, raw);
            }
            return new ParsedSummary(relaxedTitle, relaxedSummary);
        }

        String title = root.path("title").isTextual() ? root.path("title").asText() : null;
        String summary = root.path("summary").isTextual() ? root.path("summary").asText() : null;
        if (summary == null || summary.trim().isBlank()) {
            return new ParsedSummary(title, raw);
        }
        return new ParsedSummary(title, summary);
    }

    private JsonNode parseJsonObjectLenient(String text) {
        if (text == null || text.isBlank()) return null;
        List<String> candidates = List.of(text, decodeEscapedContent(text));
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                JsonNode node = objectMapper.readTree(candidate);
                if (node != null && node.isTextual()) {
                    node = objectMapper.readTree(node.asText());
                }
                if (node != null && node.isObject()) return node;
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private static String decodeEscapedContent(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char n = text.charAt(i + 1);
                switch (n) {
                    case '"' -> {
                        out.append('"');
                        i += 2;
                        continue;
                    }
                    case '\\' -> {
                        out.append('\\');
                        i += 2;
                        continue;
                    }
                    case '/' -> {
                        out.append('/');
                        i += 2;
                        continue;
                    }
                    case 'b' -> {
                        out.append('\b');
                        i += 2;
                        continue;
                    }
                    case 'f' -> {
                        out.append('\f');
                        i += 2;
                        continue;
                    }
                    case 'n' -> {
                        out.append('\n');
                        i += 2;
                        continue;
                    }
                    case 'r' -> {
                        out.append('\r');
                        i += 2;
                        continue;
                    }
                    case 't' -> {
                        out.append('\t');
                        i += 2;
                        continue;
                    }
                    case 'u' -> {
                        if (i + 5 < text.length()) {
                            String hex = text.substring(i + 2, i + 6);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (Exception ignore) {
                            }
                            i += 6;
                            continue;
                        }
                    }
                    default -> {
                        out.append(n);
                        i += 2;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String extractBetween(String text, String startToken, String endToken) {
        if (text == null || startToken == null || endToken == null) return null;
        int start = text.indexOf(startToken);
        if (start < 0) return null;
        start += startToken.length();
        int end = text.indexOf(endToken, start);
        if (end < 0 || end < start) return null;
        return text.substring(start, end);
    }

    private static String renderPrompt(String template, String title, String content, String tagsLine) {
        String out = (template == null || template.isBlank()) ? "" : template;
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
