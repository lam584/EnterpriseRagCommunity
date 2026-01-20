package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.ai.client.BailianOpenAiSseClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminModerationLlmService {

    private final ModerationLlmConfigRepository configRepository;
    private final ModerationQueueRepository queueRepository;
    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final AiProperties aiProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public LlmModerationConfigDTO getConfig() {
        ModerationLlmConfigEntity cfg = configRepository.findAll().stream()
                .max(Comparator.comparing(ModerationLlmConfigEntity::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        if (cfg == null) {
            return toDto(defaultEntity(), null);
        }
        return toDto(cfg, null);
    }

    @Transactional
    public LlmModerationConfigDTO upsertConfig(LlmModerationConfigDTO payload, Long actorUserId, String actorUsername) {
        if (payload == null || payload.getPromptTemplate() == null || payload.getPromptTemplate().isBlank()) {
            throw new IllegalArgumentException("promptTemplate 不能为空");
        }

        ModerationLlmConfigEntity cfg = configRepository.findAll().stream()
                .findFirst()
                .orElseGet(this::defaultEntity);

        cfg.setPromptTemplate(payload.getPromptTemplate());
        cfg.setModel(blankToNull(payload.getModel()));
        cfg.setTemperature(payload.getTemperature());
        cfg.setMaxTokens(payload.getMaxTokens());
        cfg.setThreshold(payload.getThreshold());
        cfg.setAutoRun(payload.getAutoRun() != null ? payload.getAutoRun() : Boolean.FALSE);
        cfg.setMaxConcurrent(payload.getMaxConcurrent());
        cfg.setMinDelayMs(payload.getMinDelayMs());
        cfg.setQps(payload.getQps());
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(actorUserId);

        cfg = configRepository.save(cfg);

        return toDto(cfg, actorUsername);
    }

    @Transactional(readOnly = true)
    public LlmModerationTestResponse test(LlmModerationTestRequest req) {
        if (req == null) throw new IllegalArgumentException("请求不能为空");

        // base config: DB or default
        ModerationLlmConfigEntity base = configRepository.findAll().stream().findFirst().orElseGet(this::defaultEntity);

        // merge overrides
        ModerationLlmConfigEntity merged = merge(base, req.getConfigOverride());

        String text = resolveText(req);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text 不能为空（或 queueId 无法解析到内容）");
        if (text.length() > 6000) text = text.substring(0, 6000); // avoid huge prompt cost

        String prompt = (merged.getPromptTemplate() == null || merged.getPromptTemplate().isBlank())
                ? DefaultLlmModerationPrompt.TEMPLATE
                : merged.getPromptTemplate();
        prompt = prompt.replace("{{text}}", text);

        String model = (merged.getModel() != null && !merged.getModel().isBlank()) ? merged.getModel() : aiProperties.getModel();
        Double temperature = merged.getTemperature();
        if (temperature == null) temperature = 0.2;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是一个严格的内容安全审核助手。"));
        messages.add(Map.of("role", "user", "content", prompt));

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
        ParsedDecision parsed = parseDecisionFromAssistantText(assistantText);

        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setDecision(parsed.decision);
        resp.setScore(parsed.score);
        resp.setReasons(parsed.reasons);
        resp.setRiskTags(parsed.riskTags);
        resp.setRawModelOutput(assistantText);
        resp.setModel(model);
        resp.setLatencyMs(latency);

        // Usage parsing (optional, best-effort)
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode usage = root.path("usage");
            if (usage != null && usage.isObject()) {
                LlmModerationTestResponse.Usage u = new LlmModerationTestResponse.Usage();
                if (usage.path("prompt_tokens").isNumber()) u.setPromptTokens(usage.path("prompt_tokens").asInt());
                if (usage.path("completion_tokens").isNumber()) u.setCompletionTokens(usage.path("completion_tokens").asInt());
                if (usage.path("total_tokens").isNumber()) u.setTotalTokens(usage.path("total_tokens").asInt());
                resp.setUsage(u);
            }
        } catch (Exception ignore) {
        }

        // apply threshold if model didn't provide decision reliably
        Double threshold = merged.getThreshold();
        if (threshold == null) threshold = 0.75;
        if ((resp.getDecision() == null || resp.getDecision().isBlank()) && resp.getScore() != null) {
            resp.setDecision(resp.getScore() >= threshold ? "REJECT" : "APPROVE");
        }

        return resp;
    }

    private String resolveText(LlmModerationTestRequest req) {
        if (req.getText() != null && !req.getText().isBlank()) return req.getText();
        if (req.getQueueId() == null) return null;

        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null) return null;

        if (q.getContentType() == ContentType.POST) {
            var p = postsRepository.findById(q.getContentId()).orElse(null);
            if (p == null) return null;
            String title = p.getTitle() == null ? "" : p.getTitle();
            String content = p.getContent() == null ? "" : p.getContent();
            return ("[POST]\n标题: " + title + "\n内容: " + content).trim();
        }
        if (q.getContentType() == ContentType.COMMENT) {
            var c = commentsRepository.findById(q.getContentId()).orElse(null);
            if (c == null) return null;
            String content = c.getContent() == null ? "" : c.getContent();
            return ("[COMMENT]\n内容: " + content).trim();
        }

        return null;
    }

    private ModerationLlmConfigEntity merge(ModerationLlmConfigEntity base, LlmModerationTestRequest.LlmModerationConfigOverrideDTO o) {
        ModerationLlmConfigEntity m = new ModerationLlmConfigEntity();
        m.setId(base.getId());
        m.setPromptTemplate(base.getPromptTemplate());
        m.setModel(base.getModel());
        m.setTemperature(base.getTemperature());
        m.setMaxTokens(base.getMaxTokens());
        m.setThreshold(base.getThreshold());
        m.setAutoRun(base.getAutoRun());
        m.setVersion(base.getVersion());
        m.setUpdatedAt(base.getUpdatedAt());
        m.setUpdatedBy(base.getUpdatedBy());
        m.setMaxConcurrent(base.getMaxConcurrent());
        m.setMinDelayMs(base.getMinDelayMs());
        m.setQps(base.getQps());

        if (o == null) return m;
        if (o.getPromptTemplate() != null) m.setPromptTemplate(o.getPromptTemplate());
        if (o.getModel() != null) m.setModel(o.getModel());
        if (o.getTemperature() != null) m.setTemperature(o.getTemperature());
        if (o.getMaxTokens() != null) m.setMaxTokens(o.getMaxTokens());
        if (o.getThreshold() != null) m.setThreshold(o.getThreshold());
        if (o.getAutoRun() != null) m.setAutoRun(o.getAutoRun());
        if (o.getMaxConcurrent() != null) m.setMaxConcurrent(o.getMaxConcurrent());
        if (o.getMinDelayMs() != null) m.setMinDelayMs(o.getMinDelayMs());
        if (o.getQps() != null) m.setQps(o.getQps());
        return m;
    }

    private ModerationLlmConfigEntity defaultEntity() {
        ModerationLlmConfigEntity e = new ModerationLlmConfigEntity();
        e.setPromptTemplate(DefaultLlmModerationPrompt.TEMPLATE);
        e.setModel(null);
        e.setTemperature(0.2);
        e.setMaxTokens(null);
        e.setThreshold(0.75);
        e.setAutoRun(Boolean.FALSE);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        e.setMaxConcurrent(4);
        e.setMinDelayMs(0);
        e.setQps(0.0);
        return e;
    }

    private LlmModerationConfigDTO toDto(ModerationLlmConfigEntity e, String updatedByName) {
        LlmModerationConfigDTO dto = new LlmModerationConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setPromptTemplate(e.getPromptTemplate());
        dto.setModel(e.getModel());
        dto.setTemperature(e.getTemperature());
        dto.setMaxTokens(e.getMaxTokens());
        dto.setThreshold(e.getThreshold());
        dto.setAutoRun(e.getAutoRun());
        dto.setMaxConcurrent(e.getMaxConcurrent());
        dto.setMinDelayMs(e.getMinDelayMs());
        dto.setQps(e.getQps());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String extractAssistantContent(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode contentNode = first.path("message").path("content");
                if (contentNode.isTextual()) return contentNode.asText();
                JsonNode textNode = first.path("text");
                if (textNode.isTextual()) return textNode.asText();
            }
        } catch (Exception ignore) {
        }
        return rawJson;
    }

    private ParsedDecision parseDecisionFromAssistantText(String assistantText) {
        if (assistantText == null) assistantText = "";
        String t = assistantText.trim();

        // extract first {...}
        int l = t.indexOf('{');
        int r = t.lastIndexOf('}');
        String json = (l >= 0 && r > l) ? t.substring(l, r + 1) : t;

        try {
            JsonNode root = objectMapper.readTree(json);
            ParsedDecision out = new ParsedDecision();
            out.decision = textOrNull(root.path("decision"));
            if (root.path("score").isNumber()) out.score = root.path("score").asDouble();

            out.reasons = new ArrayList<>();
            JsonNode reasons = root.path("reasons");
            if (reasons.isArray()) {
                for (JsonNode n : reasons) {
                    if (n.isTextual()) out.reasons.add(n.asText());
                }
            }

            out.riskTags = new ArrayList<>();
            JsonNode riskTags = root.path("riskTags");
            if (riskTags.isArray()) {
                for (JsonNode n : riskTags) {
                    if (n.isTextual()) out.riskTags.add(n.asText());
                }
            }

            // normalize
            out.decision = normalizeDecision(out.decision, out.score);
            if (out.score != null) {
                if (out.score < 0) out.score = 0.0;
                if (out.score > 1) out.score = 1.0;
            }
            return out;
        } catch (Exception e) {
            // tolerate parse errors
            ParsedDecision out = new ParsedDecision();
            out.decision = "HUMAN";
            out.score = null;
            out.reasons = List.of("模型输出无法解析为JSON");
            out.riskTags = List.of("PARSE_ERROR");
            return out;
        }
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        return String.valueOf(n);
    }

    private static String normalizeDecision(String decision, Double score) {
        if (decision == null) return null;
        String d = decision.trim().toUpperCase(Locale.ROOT);
        if (d.equals("APPROVE") || d.equals("REJECT") || d.equals("HUMAN")) return d;

        // tolerate Chinese
        if (decision.contains("通过")) return "APPROVE";
        if (decision.contains("拒绝") || decision.contains("违规")) return "REJECT";
        if (decision.contains("人工")) return "HUMAN";

        // fallback based on score
        if (score != null) return score >= 0.75 ? "REJECT" : "APPROVE";
        return "HUMAN";
    }

    private static class ParsedDecision {
        String decision;
        Double score;
        List<String> reasons;
        List<String> riskTags;
    }
}

