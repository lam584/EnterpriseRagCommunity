package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.client.BailianOpenAiSseClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiSemanticTranslateService {

    private final AiProperties aiProperties;
    private final SemanticTranslateConfigService semanticTranslateConfigService;
    private final SemanticTranslateHistoryRepository semanticTranslateHistoryRepository;
    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;

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

        String sourceHash = sha256Hex(sourceType + "|" + normalizedTitle + "\n\n" + normalizedContent);
        String configHash = sha256Hex(buildConfigSignature(cfg));

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
            out.setModel(cached.getModel());
            out.setLatencyMs(cached.getLatencyMs());
            out.setCached(Boolean.TRUE);
            return out;
        }

        String model = (cfg.getModel() != null && !cfg.getModel().isBlank()) ? cfg.getModel() : aiProperties.getModel();
        Double temperature = cfg.getTemperature();
        if (temperature == null) temperature = 0.2;

        String userPrompt = renderPrompt(cfg.getPromptTemplate(), safeTargetLang, normalizedTitle, normalizedContent);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", cfg.getSystemPrompt()));
        messages.add(Map.of("role", "user", "content", userPrompt));

        long started = System.currentTimeMillis();
        String rawJson;
        try {
            rawJson = new BailianOpenAiSseClient(aiProperties)
                    .chatCompletionsOnce(null, aiProperties.getBaseUrl(), model, messages, temperature);
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
            h.setModel(model);
            h.setTemperature(temperature);
            h.setLatencyMs(latency);
            h.setPromptVersion(cfg.getVersion());
            semanticTranslateConfigService.recordHistory(h);
        }

        SemanticTranslateResultDTO out = new SemanticTranslateResultDTO();
        out.setTargetLang(safeTargetLang);
        out.setTranslatedTitle(blankToNull(translatedTitle));
        out.setTranslatedMarkdown(translatedMarkdown);
        out.setModel(model);
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
        String t = assistantText.trim();

        String json = t;
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
        String safeTemplate = (template == null || template.isBlank())
                ? SemanticTranslateConfigService.DEFAULT_PROMPT_TEMPLATE
                : template;
        String out = safeTemplate;
        out = out.replace("{{targetLang}}", targetLang == null ? "" : targetLang.trim());
        out = out.replace("{{title}}", title == null ? "" : title.trim());
        out = out.replace("{{content}}", content == null ? "" : content.trim());
        return out;
    }

    private static String buildConfigSignature(SemanticTranslateConfigEntity cfg) {
        String model = cfg.getModel() == null ? "" : cfg.getModel().trim();
        String temp = cfg.getTemperature() == null ? "" : String.valueOf(cfg.getTemperature());
        String max = cfg.getMaxContentChars() == null ? "" : String.valueOf(cfg.getMaxContentChars());
        String sp = cfg.getSystemPrompt() == null ? "" : cfg.getSystemPrompt().trim();
        String pt = cfg.getPromptTemplate() == null ? "" : cfg.getPromptTemplate().trim();
        return "model=" + model + "|temp=" + temp + "|max=" + max + "|sp=" + sp + "|pt=" + pt;
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
