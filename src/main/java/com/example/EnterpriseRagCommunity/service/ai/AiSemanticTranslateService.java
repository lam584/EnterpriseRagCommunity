package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AiSemanticTranslateService {

    private final SemanticTranslateConfigService semanticTranslateConfigService;
    private final SemanticTranslateHistoryRepository semanticTranslateHistoryRepository;
    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final LlmGateway llmGateway;
    private final PromptsRepository promptsRepository;
    private final GenerationJobsRepository generationJobsRepository;
    private final PromptLlmParamResolver promptLlmParamResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SemanticTranslateResultDTO translatePost(Long postId, String targetLang, Long actorUserId) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        PostsEntity post = postsRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));

        String title = post.getTitle() == null ? "" : post.getTitle().trim();
        String content = post.getContent() == null ? "" : post.getContent().trim();
        return translateOnce("POST", postId, title, content, targetLang, actorUserId);
    }

    public SemanticTranslateResultDTO translateComment(Long commentId, String targetLang, Long actorUserId) {
        if (commentId == null) throw new IllegalArgumentException("commentId 不能为空");
        CommentsEntity c = commentsRepository.findById(commentId)
                .filter(x -> !Boolean.TRUE.equals(x.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));

        String content = c.getContent() == null ? "" : c.getContent().trim();
        return translateOnce("COMMENT", commentId, null, content, targetLang, actorUserId);
    }

    public SseEmitter translatePostStream(Long postId, String targetLang, Long actorUserId) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        PostsEntity post = postsRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));

        String title = post.getTitle() == null ? "" : post.getTitle().trim();
        String content = post.getContent() == null ? "" : post.getContent().trim();
        return translateStreamOnce("POST", postId, title, content, targetLang, actorUserId);
    }

    public SseEmitter translateCommentStream(Long commentId, String targetLang, Long actorUserId) {
        if (commentId == null) throw new IllegalArgumentException("commentId 不能为空");
        CommentsEntity c = commentsRepository.findById(commentId)
                .filter(x -> !Boolean.TRUE.equals(x.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));

        String content = c.getContent() == null ? "" : c.getContent().trim();
        return translateStreamOnce("COMMENT", commentId, null, content, targetLang, actorUserId);
    }

    private SseEmitter translateStreamOnce(
            String sourceType,
            Long sourceId,
            String title,
            String content,
            String targetLang,
            Long actorUserId
    ) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes
        if (targetLang == null || targetLang.trim().isEmpty()) {
            emitter.completeWithError(new IllegalArgumentException("targetLang 不能为空"));
            return emitter;
        }
        String safeTargetLang = targetLang.trim();

        SemanticTranslateConfigEntity cfg = semanticTranslateConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            emitter.completeWithError(new IllegalStateException("翻译功能已关闭"));
            return emitter;
        }

        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedContent = content == null ? "" : content.trim();

        int maxChars = cfg.getMaxContentChars() == null ? SemanticTranslateConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && normalizedContent.length() > maxChars) {
            normalizedContent = normalizedContent.substring(0, maxChars);
        }
        String normalizedContentFinal = normalizedContent;

        String promptCode = cfg.getPromptCode();
        if (promptCode == null || promptCode.isBlank()) {
            promptCode = SemanticTranslateConfigService.DEFAULT_PROMPT_CODE;
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
            0.2,
            0.4
        );

        String sourceHash = sha256Hex(sourceType + "|" + normalizedTitle + "\n\n" + normalizedContentFinal);
        String configHash = sha256Hex(buildConfigSignature(cfg, prompt, params));

        var cached = semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(
                        sourceType, sourceId, safeTargetLang, sourceHash, configHash
                )
                .orElse(null);

        if (cached != null) {
            try {
                SemanticTranslateResultDTO out = new SemanticTranslateResultDTO();
                out.setTargetLang(safeTargetLang);
                out.setTranslatedTitle(cached.getTranslatedTitle());
                out.setTranslatedMarkdown(cached.getTranslatedMarkdown());
                out.setModel(null);
                out.setLatencyMs(null);
                out.setCached(Boolean.TRUE);
                emitter.send(out);
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String modelOverride = params.model();
                Double temperature = params.temperature();
                Double topP = params.topP();

                String streamInstruction = "\n\n[System Note: Stream Mode Active. Please output the translated content directly in Markdown. Do NOT wrap in JSON. If there is a title, put it on the first line.]";
                String userPrompt = renderPrompt(prompt.getUserPromptTemplate() + streamInstruction, safeTargetLang, normalizedTitle, normalizedContentFinal);
                List<ChatMessage> messages = new ArrayList<>();
                messages.add(ChatMessage.system(prompt.getSystemPrompt()));
                messages.add(ChatMessage.user(userPrompt));

                StringBuilder accumulated = new StringBuilder();
                long started = System.currentTimeMillis();

                var streamRes = llmGateway.chatStreamRouted(
                        LlmQueueTaskType.UNKNOWN,
                        params.providerId(),
                        modelOverride,
                        messages,
                        temperature,
                        topP,
                        params.enableThinking(),
                        null,
                        (line) -> {
                            String textDelta = extractStreamChunkText(line);
                            if (textDelta != null && !textDelta.isEmpty()) {
                                accumulated.append(textDelta);
                                try {
                                    emitter.send(java.util.Collections.singletonMap("delta", textDelta));
                                } catch (IOException e) {
                                    throw new RuntimeException("Client send failed", e);
                                }
                            }
                        }
                );

                long latency = System.currentTimeMillis() - started;
                String fullText = accumulated.toString();

                String translatedTitle = null;
                String translatedMarkdown = fullText;

                if (!normalizedTitle.isEmpty()) {
                    int firstNl = fullText.indexOf('\n');
                    if (firstNl > 0) {
                        translatedTitle = fullText.substring(0, firstNl).trim();
                        if (translatedTitle.startsWith("#")) translatedTitle = translatedTitle.replaceFirst("^#+\\s*", "");
                        translatedMarkdown = fullText.substring(firstNl).trim();
                    }
                }

                if (Boolean.TRUE.equals(cfg.getHistoryEnabled())) {
                    GenerationJobsEntity job = new GenerationJobsEntity();
                    job.setJobType(GenerationJobType.TRANSLATE);
                    job.setTargetType("POST".equals(sourceType) ? GenerationTargetType.POST : GenerationTargetType.COMMENT);
                    job.setTargetId(sourceId);
                    job.setStatus(GenerationJobStatus.SUCCEEDED);
                    job.setModel(streamRes.model());
                    job.setProviderId(streamRes.providerId());
                    job.setTemperature(temperature);
                    job.setTopP(topP);
                    job.setLatencyMs(latency);
                    job.setPromptVersion(cfg.getVersion());
                    job.setCreatedAt(LocalDateTime.now());
                    job.setUpdatedAt(job.getCreatedAt());
                    generationJobsRepository.save(job);

                    SemanticTranslateHistoryEntity h = new SemanticTranslateHistoryEntity();
                    h.setUserId(actorUserId == null ? 0L : actorUserId);
                    h.setCreatedAt(LocalDateTime.now());
                    h.setSourceType(sourceType);
                    h.setSourceId(sourceId);
                    h.setTargetLang(safeTargetLang);
                    h.setSourceHash(sourceHash);
                    h.setConfigHash(configHash);
                    h.setSourceTitleExcerpt(buildTitleExcerpt(normalizedTitle));
                    h.setSourceContentExcerpt(buildExcerpt(normalizedContentFinal));
                    h.setTranslatedTitle(blankToNull(translatedTitle));
                    h.setTranslatedMarkdown(translatedMarkdown);
                    h.setJobId(job.getId());
                    semanticTranslateConfigService.recordHistory(h);
                }

                SemanticTranslateResultDTO finalRes = new SemanticTranslateResultDTO();
                finalRes.setTargetLang(safeTargetLang);
                finalRes.setTranslatedTitle(blankToNull(translatedTitle));
                finalRes.setTranslatedMarkdown(translatedMarkdown);
                finalRes.setModel(streamRes.model());
                finalRes.setLatencyMs(latency);
                finalRes.setCached(Boolean.FALSE);

                emitter.send(finalRes);
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String extractStreamChunkText(String line) {
        if (line == null || !line.startsWith("data:")) return null;
        String payload = line.substring(5).trim();
        if (payload.equals("[DONE]")) return null;
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                if (delta.has("content")) return delta.get("content").asText();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private SemanticTranslateResultDTO translateOnce(
            String sourceType,
            Long sourceId,
            String title,
            String content,
            String targetLang,
            Long actorUserId
    ) {
        if (targetLang == null || targetLang.trim().isEmpty()) {
            throw new IllegalArgumentException("targetLang 不能为空");
        }
        String safeTargetLang = targetLang.trim();

        SemanticTranslateConfigEntity cfg = semanticTranslateConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            throw new IllegalStateException("翻译功能已关闭");
        }

        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedContent = content == null ? "" : content.trim();

        int maxChars = cfg.getMaxContentChars() == null ? SemanticTranslateConfigService.DEFAULT_MAX_CONTENT_CHARS : cfg.getMaxContentChars();
        if (maxChars > 0 && normalizedContent.length() > maxChars) {
            normalizedContent = normalizedContent.substring(0, maxChars);
        }

        String promptCode = cfg.getPromptCode();
        if (promptCode == null || promptCode.isBlank()) {
            promptCode = SemanticTranslateConfigService.DEFAULT_PROMPT_CODE;
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
            0.2,
            0.4
        );

        String sourceHash = sha256Hex(sourceType + "|" + normalizedTitle + "\n\n" + normalizedContent);
        String configHash = sha256Hex(buildConfigSignature(cfg, prompt, params));

        var cached = semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(
                        sourceType, sourceId, safeTargetLang, sourceHash, configHash
                )
                .orElse(null);
        if (cached != null) {
            SemanticTranslateResultDTO out = new SemanticTranslateResultDTO();
            out.setTargetLang(safeTargetLang);
            out.setTranslatedTitle(cached.getTranslatedTitle());
            out.setTranslatedMarkdown(cached.getTranslatedMarkdown());
            out.setModel(null);
            out.setLatencyMs(null);
            out.setCached(Boolean.TRUE);
            return out;
        }

        String modelOverride = params.model();
        Double temperature = params.temperature();
        Double topP = params.topP();

        String userPrompt = renderPrompt(prompt.getUserPromptTemplate(), safeTargetLang, normalizedTitle, normalizedContent);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompt.getSystemPrompt()));
        messages.add(ChatMessage.user(userPrompt));

        long started = System.currentTimeMillis();
        String rawJson;
        String usedProviderId;
        String usedModel;
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(
                    LlmQueueTaskType.UNKNOWN,
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
        long latency = System.currentTimeMillis() - started;

        String assistantText = extractAssistantContent(rawJson);
        ParsedTranslate parsed = parseTranslateFromAssistantText(assistantText);
        String translatedTitle = parsed.title();
        String translatedMarkdown = parsed.markdown();
        if (translatedMarkdown == null || translatedMarkdown.trim().isEmpty()) {
            translatedMarkdown = assistantText == null ? "" : assistantText.trim();
        }

        if (Boolean.TRUE.equals(cfg.getHistoryEnabled())) {
            GenerationJobsEntity job = new GenerationJobsEntity();
            job.setJobType(GenerationJobType.TRANSLATE);
            job.setTargetType("POST".equals(sourceType) ? GenerationTargetType.POST : GenerationTargetType.COMMENT);
            job.setTargetId(sourceId);
            job.setStatus(GenerationJobStatus.SUCCEEDED);
            job.setModel(usedModel);
            job.setProviderId(usedProviderId);
            job.setTemperature(temperature);
            job.setTopP(topP);
            job.setLatencyMs(latency);
            job.setPromptVersion(cfg.getVersion());
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(job.getCreatedAt());
            generationJobsRepository.save(job);

            SemanticTranslateHistoryEntity h = new SemanticTranslateHistoryEntity();
            h.setUserId(actorUserId == null ? 0L : actorUserId);
            h.setCreatedAt(LocalDateTime.now());
            h.setSourceType(sourceType);
            h.setSourceId(sourceId);
            h.setTargetLang(safeTargetLang);
            h.setSourceHash(sourceHash);
            h.setConfigHash(configHash);
            h.setSourceTitleExcerpt(buildTitleExcerpt(normalizedTitle));
            h.setSourceContentExcerpt(buildExcerpt(normalizedContent));
            h.setTranslatedTitle(blankToNull(translatedTitle));
            h.setTranslatedMarkdown(translatedMarkdown);
            h.setJobId(job.getId());
            semanticTranslateConfigService.recordHistory(h);
        }

        SemanticTranslateResultDTO out = new SemanticTranslateResultDTO();
        out.setTargetLang(safeTargetLang);
        out.setTranslatedTitle(blankToNull(translatedTitle));
        out.setTranslatedMarkdown(translatedMarkdown);
        out.setModel(usedModel);
        out.setLatencyMs(latency);
        out.setCached(Boolean.FALSE);
        return out;
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

    private ParsedTranslate parseTranslateFromAssistantText(String assistantText) {
        if (assistantText == null) return new ParsedTranslate(null, null);
        String json = assistantText.trim();
        int l = json.indexOf('{');
        int r = json.lastIndexOf('}');
        if (l >= 0 && r > l) {
            json = json.substring(l, r + 1);
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            String title = null;
            String markdown = null;
            JsonNode titleNode = root.get("title");
            if (titleNode != null && titleNode.isTextual()) title = titleNode.asText();
            JsonNode mdNode = root.get("markdown");
            if (mdNode != null && mdNode.isTextual()) markdown = mdNode.asText();
            return new ParsedTranslate(title, markdown);
        } catch (Exception ignore) {
            return new ParsedTranslate(null, null);
        }
    }

    private static String renderPrompt(String template, String targetLang, String title, String content) {
        String out = (template == null || template.isBlank())
                ? ""
                : template;
        out = out.replace("{{targetLang}}", targetLang == null ? "" : targetLang.trim());
        out = out.replace("{{title}}", title == null ? "" : title.trim());
        out = out.replace("{{content}}", content == null ? "" : content.trim());
        return out;
    }

    private static String buildConfigSignature(SemanticTranslateConfigEntity cfg, PromptsEntity prompt, PromptLlmParams params) {
        String model = params.model() == null ? "" : params.model().trim();
        String providerId = params.providerId() == null ? "" : params.providerId().trim();
        String temp = params.temperature() == null ? "" : String.valueOf(params.temperature());
        String topP = params.topP() == null ? "" : String.valueOf(params.topP());
        String thinking = Boolean.TRUE.equals(params.enableThinking()) ? "1" : "0";
        String max = cfg.getMaxContentChars() == null ? "" : String.valueOf(cfg.getMaxContentChars());
        String sp = prompt.getSystemPrompt() == null ? "" : prompt.getSystemPrompt().trim();
        String pt = prompt.getUserPromptTemplate() == null ? "" : prompt.getUserPromptTemplate().trim();
        return "providerId=" + providerId + "|model=" + model + "|temp=" + temp + "|topP=" + topP + "|thinking=" + thinking + "|max=" + max + "|sp=" + sp + "|pt=" + pt;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed: " + e.getMessage(), e);
        }
    }

    private static String buildExcerpt(String content) {
        if (content == null) return null;
        String t = content.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 240) t = t.substring(0, 240);
        return t;
    }

    private static String buildTitleExcerpt(String title) {
        if (title == null) return null;
        String t = title.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 120) t = t.substring(0, 120);
        return t;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record ParsedTranslate(String title, String markdown) {
    }
}
