package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminModerationLlmService {

    private final ModerationLlmConfigRepository configRepository;
    private final ModerationConfidenceFallbackConfigRepository fallbackRepository;
    private final ModerationQueueRepository queueRepository;
    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final ReportsRepository reportsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetsRepository fileAssetsRepository;
    private final AiProperties aiProperties;
    private final LlmGateway llmGateway;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

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
        cfg.setVisionPromptTemplate(
                payload.getVisionPromptTemplate() == null || payload.getVisionPromptTemplate().isBlank()
                        ? defaultVisionPromptTemplate()
                        : payload.getVisionPromptTemplate()
        );
        cfg.setModel(blankToNull(payload.getModel()));
        cfg.setProviderId(blankToNull(payload.getProviderId()));
        cfg.setVisionModel(blankToNull(payload.getVisionModel()));
        cfg.setVisionProviderId(blankToNull(payload.getVisionProviderId()));
        cfg.setTemperature(payload.getTemperature());
        cfg.setVisionTemperature(payload.getVisionTemperature());
        cfg.setMaxTokens(payload.getMaxTokens());
        cfg.setVisionMaxTokens(payload.getVisionMaxTokens());
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

        PromptVars vars = resolvePromptVarsSafe(req);
        String text = vars == null ? null : vars.text;
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text 不能为空（或 queueId 无法解析到内容）");
        if (text.length() > 6000) {
            text = text.substring(0, 6000);
            vars = new PromptVars(vars.title, vars.content, text);
        }

        List<ImageRef> images = resolveImages(req);

        String promptTemplate = merged.getPromptTemplate();
        if (promptTemplate == null || promptTemplate.isBlank()) {
            throw new IllegalStateException("LLM 审核提示词未配置，请先在后台保存配置或在 configOverride 中提供 promptTemplate");
        }

        String modelOverride = blankToNull(merged.getModel());
        Double temperature = merged.getTemperature();
        if (temperature == null) temperature = 0.2;
        Integer maxTokens = merged.getMaxTokens();

        String visionPromptTemplate = merged.getVisionPromptTemplate();
        Double visionTemperature = merged.getVisionTemperature();
        if (visionTemperature == null) visionTemperature = temperature;
        Integer visionMaxTokens = merged.getVisionMaxTokens();
        if (visionMaxTokens == null) visionMaxTokens = maxTokens;

        String visionModelOverride = blankToNull(merged.getVisionModel());
        String visionProviderId = blankToNull(merged.getVisionProviderId());

        String systemPrompt = "你是一个严格的内容安全审核助手。";

        ModerationConfidenceFallbackConfigEntity fb = loadFallbackOrDefault();
        double textRiskThreshold = clamp01(fb.getLlmTextRiskThreshold(), 0.80);
        double imageRiskThreshold = clamp01(fb.getLlmImageRiskThreshold(), 0.30);
        double strongRejectThreshold = clamp01(fb.getLlmStrongRejectThreshold(), 0.95);
        double strongPassThreshold = clamp01(fb.getLlmStrongPassThreshold(), 0.10);
        double crossModalThreshold = clamp01(fb.getLlmCrossModalThreshold(), 0.75);

        String textPrompt = renderTextPrompt(promptTemplate, vars);

        if (images == null || images.isEmpty()) {
            StageCallResult one = callTextOnce(systemPrompt, textPrompt, temperature, maxTokens, merged.getProviderId(), modelOverride);
            LlmModerationTestResponse resp = new LlmModerationTestResponse();
            resp.setDecision(one.decision);
            resp.setScore(one.score);
            resp.setReasons(one.reasons);
            resp.setRiskTags(one.riskTags);
            resp.setRawModelOutput(one.rawModelOutput);
            resp.setModel(one.model);
            resp.setLatencyMs(one.latencyMs);
            resp.setUsage(one.usage);
            resp.setPromptMessages(one.promptMessages);
            resp.setImages(List.of());
            resp.setInputMode(one.inputMode);

            Double threshold = merged.getThreshold();
            if (threshold == null) threshold = 0.75;
            if ((resp.getDecision() == null || resp.getDecision().isBlank()) && resp.getScore() != null) {
                resp.setDecision(resp.getScore() >= threshold ? "REJECT" : "APPROVE");
            }
            return resp;
        }

        List<String> urls = new ArrayList<>();
        for (ImageRef img : images) {
            if (img == null) continue;
            if (img.url() == null || img.url().isBlank()) continue;
            urls.add(img.url().trim());
            if (urls.size() >= 5) break;
        }

        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();

        StageCallResult textStage = callTextOnce(systemPrompt, textPrompt, temperature, maxTokens, merged.getProviderId(), modelOverride);
        stages.setText(toStage(textStage, null));
        if ("HUMAN".equalsIgnoreCase(textStage.decision)) {
            return finalizeMultiStage("HUMAN", null, List.of("文本审核失败，已转人工"), List.of("UPSTREAM_ERROR"), stages, urls);
        }

        Double textScore = textStage.score;
        double ts = textScore == null ? 0.0 : clamp01(textScore, 0.0);
        String textDecision = ts >= textRiskThreshold ? "REJECT" : "APPROVE";
        stages.getText().setDecision(textDecision);

        if (ts >= strongRejectThreshold) {
            List<String> reasons = new ArrayList<>();
            reasons.add("强拒绝：文本高置信违规（>= " + strongRejectThreshold + "）");
            return finalizeMultiStage("REJECT", ts, reasons, textStage.riskTags, stages, urls);
        }

        StageCallResult imageStage = callImageDescribeOnce(systemPrompt, vars, images, visionPromptTemplate, visionTemperature, visionMaxTokens, visionProviderId, visionModelOverride);
        stages.setImage(toStage(imageStage, imageStage.description));
        if ("HUMAN".equalsIgnoreCase(imageStage.decision)) {
            return finalizeMultiStage("HUMAN", null, List.of("图片审核失败，已转人工"), List.of("UPSTREAM_ERROR"), stages, urls);
        }

        Double imageScore = imageStage.score;
        double is = imageScore == null ? 0.0 : clamp01(imageScore, 0.0);

        String imageDecision = is >= imageRiskThreshold ? "REJECT" : "APPROVE";
        stages.getImage().setDecision(imageDecision);

        if (ts >= strongRejectThreshold || is >= strongRejectThreshold) {
            List<String> reasons = new ArrayList<>();
            reasons.add("强拒绝：存在高置信违规（>= " + strongRejectThreshold + "）");
            return finalizeMultiStage("REJECT", Math.max(ts, is), reasons, mergeTags(textStage.riskTags, imageStage.riskTags), stages, urls);
        }

        if (ts < strongPassThreshold && is < strongPassThreshold) {
            List<String> reasons = new ArrayList<>();
            reasons.add("强通过：文本与图片均低风险（< " + strongPassThreshold + "）");
            return finalizeMultiStage("APPROVE", Math.max(ts, is), reasons, mergeTags(textStage.riskTags, imageStage.riskTags), stages, urls);
        }

        String crossPrompt = buildCrossModalPrompt(text, imageStage.description, ts, is, textStage.reasons, imageStage.reasons);
        StageCallResult crossStage = callTextOnce(systemPrompt, crossPrompt, temperature, maxTokens, merged.getProviderId(), modelOverride);
        stages.setCross(toStage(crossStage, null));

        if ("HUMAN".equalsIgnoreCase(crossStage.decision)) {
            return finalizeMultiStage("HUMAN", null, List.of("跨模态复核失败，已转人工"), mergeTags(mergeTags(textStage.riskTags, imageStage.riskTags), crossStage.riskTags), stages, urls);
        }

        double cs = crossStage.score == null ? 0.0 : clamp01(crossStage.score, 0.0);
        String finalDecision = crossStage.decision;
        if (finalDecision == null || finalDecision.isBlank() || (!"APPROVE".equalsIgnoreCase(finalDecision) && !"REJECT".equalsIgnoreCase(finalDecision) && !"HUMAN".equalsIgnoreCase(finalDecision))) {
            finalDecision = cs >= crossModalThreshold ? "REJECT" : "APPROVE";
        }
        if ("REJECT".equalsIgnoreCase(finalDecision) && cs < crossModalThreshold) {
            finalDecision = "APPROVE";
        }
        if ("APPROVE".equalsIgnoreCase(finalDecision) && cs >= crossModalThreshold) {
            finalDecision = "REJECT";
        }

        List<String> finalReasons = new ArrayList<>();
        finalReasons.add("跨模态复核：综合判定阈值 " + crossModalThreshold);
        if (crossStage.reasons != null && !crossStage.reasons.isEmpty()) finalReasons.addAll(crossStage.reasons);

        return finalizeMultiStage(finalDecision.toUpperCase(Locale.ROOT), cs, finalReasons, mergeTags(mergeTags(textStage.riskTags, imageStage.riskTags), crossStage.riskTags), stages, urls, crossStage);
    }

    private ModerationConfidenceFallbackConfigEntity loadFallbackOrDefault() {
        ModerationConfidenceFallbackConfigEntity fb = null;
        try {
            fb = fallbackRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"))
                    .stream().findFirst().orElse(null);
        } catch (Exception ignored) {
        }
        if (fb == null) fb = defaultFallback();
        return fb;
    }

    private static ModerationConfidenceFallbackConfigEntity defaultFallback() {
        ModerationConfidenceFallbackConfigEntity e = new ModerationConfidenceFallbackConfigEntity();
        e.setLlmTextRiskThreshold(0.80);
        e.setLlmImageRiskThreshold(0.30);
        e.setLlmStrongRejectThreshold(0.95);
        e.setLlmStrongPassThreshold(0.10);
        e.setLlmCrossModalThreshold(0.75);
        return e;
    }

    private static double clamp01(Double v, double def) {
        if (v == null || !Double.isFinite(v)) return def;
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    private static List<String> mergeTags(List<String> a, List<String> b) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (a != null) set.addAll(a);
        if (b != null) set.addAll(b);
        return set.isEmpty() ? null : new ArrayList<>(set);
    }

    private static LlmModerationTestResponse.Stage toStage(StageCallResult r, String desc) {
        if (r == null) return null;
        LlmModerationTestResponse.Stage s = new LlmModerationTestResponse.Stage();
        s.setDecision(r.decision);
        s.setScore(r.score);
        s.setReasons(r.reasons);
        s.setRiskTags(r.riskTags);
        s.setRawModelOutput(r.rawModelOutput);
        s.setModel(r.model);
        s.setLatencyMs(r.latencyMs);
        s.setUsage(r.usage);
        s.setDescription(desc);
        s.setInputMode(r.inputMode);
        return s;
    }

    private LlmModerationTestResponse finalizeMultiStage(
            String decision,
            Double score,
            List<String> reasons,
            List<String> riskTags,
            LlmModerationTestResponse.Stages stages,
            List<String> images
    ) {
        return finalizeMultiStage(decision, score, reasons, riskTags, stages, images, null);
    }

    private LlmModerationTestResponse finalizeMultiStage(
            String decision,
            Double score,
            List<String> reasons,
            List<String> riskTags,
            LlmModerationTestResponse.Stages stages,
            List<String> images,
            StageCallResult finalStage
    ) {
        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setDecision(decision);
        resp.setScore(score);
        resp.setReasons(reasons);
        resp.setRiskTags(riskTags);
        resp.setStages(stages);
        resp.setImages(images == null ? List.of() : images);
        resp.setInputMode("multistage");
        if (finalStage != null) {
            resp.setRawModelOutput(finalStage.rawModelOutput);
            resp.setModel(finalStage.model);
            resp.setLatencyMs(finalStage.latencyMs);
            resp.setUsage(finalStage.usage);
            resp.setPromptMessages(finalStage.promptMessages);
        } else if (stages != null && stages.getCross() != null) {
            resp.setRawModelOutput(stages.getCross().getRawModelOutput());
            resp.setModel(stages.getCross().getModel());
            resp.setLatencyMs(stages.getCross().getLatencyMs());
            resp.setUsage(stages.getCross().getUsage());
        } else if (stages != null && stages.getImage() != null) {
            resp.setRawModelOutput(stages.getImage().getRawModelOutput());
            resp.setModel(stages.getImage().getModel());
            resp.setLatencyMs(stages.getImage().getLatencyMs());
            resp.setUsage(stages.getImage().getUsage());
        } else if (stages != null && stages.getText() != null) {
            resp.setRawModelOutput(stages.getText().getRawModelOutput());
            resp.setModel(stages.getText().getModel());
            resp.setLatencyMs(stages.getText().getLatencyMs());
            resp.setUsage(stages.getText().getUsage());
        }
        return resp;
    }

    private StageCallResult callTextOnce(
            String systemPrompt,
            String userPrompt,
            Double temperature,
            Integer maxTokens,
            String providerId,
            String modelOverride
    ) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));
        return callOnce(LlmQueueTaskType.TEXT_MODERATION, providerId, modelOverride, messages, temperature, maxTokens, "text");
    }

    private StageCallResult callImageDescribeOnce(
            String systemPrompt,
            PromptVars vars,
            List<ImageRef> images,
            String promptTemplate,
            Double temperature,
            Integer maxTokens,
            String providerId,
            String modelOverride
    ) {
        String instruction = renderVisionPrompt(promptTemplate, vars);
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", instruction));
        for (ImageRef img : images) {
            if (img == null) continue;
            String u = encodeImageUrlForUpstream(img);
            if (u == null || u.isBlank()) continue;
            parts.add(Map.of("type", "image_url", "image_url", Map.of("url", u.trim())));
        }
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.userParts(parts));
        return callOnce(LlmQueueTaskType.IMAGE_MODERATION, providerId, modelOverride, messages, temperature, maxTokens, "multimodal");
    }

    private StageCallResult callOnce(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens,
            String inputMode
    ) {
        long started = System.currentTimeMillis();
        try {
            LlmGateway.RoutedChatOnceResult routed = llmGateway.chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, maxTokens, null);
            long latency = System.currentTimeMillis() - started;
            String rawJson = routed == null ? null : routed.text();
            String assistantText = extractAssistantContent(rawJson);
            ParsedDecision parsed = parseDecisionFromAssistantText(assistantText);
            LlmModerationTestResponse.Usage usage = null;
            if (routed != null && routed.usage() != null) {
                usage = new LlmModerationTestResponse.Usage();
                usage.setPromptTokens(routed.usage().promptTokens());
                usage.setCompletionTokens(routed.usage().completionTokens());
                usage.setTotalTokens(routed.usage().totalTokens());
            }
            List<LlmModerationTestResponse.Message> promptMessages = new ArrayList<>();
            if (messages != null) {
                for (ChatMessage m : messages) {
                    if (m == null) continue;
                    String role = m.role();
                    if (role == null || role.isBlank()) continue;
                    Object c = m.content();
                    String content;
                    if (c == null) content = "";
                    else if (c instanceof String s) content = s;
                    else content = "[non_text_content]";
                    LlmModerationTestResponse.Message pm = new LlmModerationTestResponse.Message();
                    pm.setRole(role);
                    pm.setContent(content);
                    promptMessages.add(pm);
                }
            }
            return new StageCallResult(
                    parsed == null ? "HUMAN" : parsed.decision,
                    parsed == null ? null : parsed.score,
                    parsed == null ? List.of("模型输出无法解析为JSON") : parsed.reasons,
                    parsed == null ? List.of("PARSE_ERROR") : parsed.riskTags,
                    assistantText,
                    routed == null ? null : routed.model(),
                    latency,
                    usage,
                    promptMessages.isEmpty() ? null : promptMessages,
                    parsed == null ? null : parsed.description,
                    inputMode
            );
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - started;
            return new StageCallResult(
                    "HUMAN",
                    null,
                    List.of("上游AI调用失败: " + e.getMessage()),
                    List.of("UPSTREAM_ERROR"),
                    null,
                    null,
                    latency,
                    null,
                    null,
                    null,
                    inputMode
            );
        }
    }

    private static String renderVisionPrompt(String promptTemplate, PromptVars vars) {
        String tpl = promptTemplate == null ? "" : promptTemplate;
        if (tpl.isBlank()) tpl = defaultVisionPromptTemplate();
        String title = vars == null ? "" : nullToEmpty(vars.title).trim();
        String content = vars == null ? "" : nullToEmpty(vars.content).trim();
        String t = vars == null ? "" : nullToEmpty(vars.text).trim();
        if (t.length() > 1000) t = t.substring(0, 1000);
        return tpl
                .replace("{{text}}", t)
                .replace("{{title}}", title)
                .replace("{{content}}", content);
    }

    private static String renderTextPrompt(String promptTemplate, PromptVars vars) {
        String tpl = promptTemplate == null ? "" : promptTemplate;
        String title = vars == null ? "" : nullToEmpty(vars.title);
        String content = vars == null ? "" : nullToEmpty(vars.content);
        String text = vars == null ? "" : nullToEmpty(vars.text);
        return tpl
                .replace("{{text}}", text)
                .replace("{{title}}", title)
                .replace("{{content}}", content);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static class PromptVars {
        final String title;
        final String content;
        final String text;

        PromptVars(String title, String content, String text) {
            this.title = title;
            this.content = content;
            this.text = text;
        }
    }

    private static String defaultVisionPromptTemplate() {
        return """
                你是一个严格的图片内容安全审核助手。请你同时完成“描述图片内容”与“判断是否违规”两项任务，并且必须只输出严格 JSON。

                你会收到若干张图片（最多 5 张）。请综合所有图片，输出：
                {
                  "decision": "APPROVE|REJECT|HUMAN",
                  "score": 0.0-1.0,
                  "reasons": ["..."],
                  "riskTags": ["..."],
                  "description": "请用中文描述图片里有什么：人物、文字(尽量OCR)、场景、动作、关系、可能的广告/引流信息等"
                }

                注意：
                - score 表示“图片整体违规风险概率(0~1)”，越大越可能违规；
                - decision 仅能取 APPROVE/REJECT/HUMAN；
                - 若图片含文字，请尽量转写关键文字；
                - 如果你无法确定或图片不可读，decision=HUMAN。

                关联的原文（供你理解上下文，不代表一定要参考）：
                {{text}}""";
    }

    private static String buildCrossModalPrompt(
            String originalText,
            String imageDescription,
            double textScore,
            double imageScore,
            List<String> textReasons,
            List<String> imageReasons
    ) {
        String t = originalText == null ? "" : originalText.trim();
        if (t.length() > 3000) t = t.substring(0, 3000);
        String desc = imageDescription == null ? "" : imageDescription.trim();
        if (desc.length() > 2000) desc = desc.substring(0, 2000);
        String tr = (textReasons == null || textReasons.isEmpty()) ? "" : String.join("；", textReasons);
        String ir = (imageReasons == null || imageReasons.isEmpty()) ? "" : String.join("；", imageReasons);
        return """
                你是一个严格的内容安全审核助手。你将收到：原始文字、图片描述、以及两次初步风险评分。请进行跨模态一致性验证，并输出严格 JSON：
                {
                  "decision": "APPROVE|REJECT|HUMAN",
                  "score": 0.0-1.0,
                  "reasons": ["..."],
                  "riskTags": ["..."]
                }

                score 表示“综合违规风险概率(0~1)”。若信息矛盾或你无法确定，请 decision=HUMAN。

                [TEXT]
                """ + t + """

                [IMAGE_DESCRIPTION]
                """ + desc + """

                [PRELIMINARY]
                - textScore: """ + textScore + """
                - imageScore: """ + imageScore + """
                - textReasons: """ + tr + """
                - imageReasons: """ + ir;
    }

    private record StageCallResult(
            String decision,
            Double score,
            List<String> reasons,
            List<String> riskTags,
            String rawModelOutput,
            String model,
            Long latencyMs,
            LlmModerationTestResponse.Usage usage,
            List<LlmModerationTestResponse.Message> promptMessages,
            String description,
            String inputMode
    ) {}

    private static boolean providerSupportsVision(AiProvidersConfigService.ResolvedProvider provider) {
        if (provider == null || provider.metadata() == null) return false;
        Object v = provider.metadata().get("supportsVision");
        return (v instanceof Boolean b) && b;
    }

    private record ImageRef(Long fileAssetId, String url, String mimeType) {}

    private static boolean isLikelyImageUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("/uploads/")) return true;
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.endsWith(".svg");
    }

    private String encodeImageUrlForUpstream(ImageRef img) {
        if (img == null) return null;
        String url = blankToNull(img.url());
        if (url == null) return null;

        if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        byte[] bytes = readLocalUploadBytes(img.fileAssetId(), url);
        if (bytes == null || bytes.length == 0) return url;
        if (bytes.length > 4_000_000) return url;

        String mimeType = blankToNull(img.mimeType());
        if ((mimeType == null || mimeType.isBlank()) && img.fileAssetId() != null) {
            var fa = fileAssetsRepository.findById(img.fileAssetId()).orElse(null);
            mimeType = fa == null ? null : blankToNull(fa.getMimeType());
        }
        if (mimeType == null || mimeType.isBlank()) mimeType = "application/octet-stream";

        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] readLocalUploadBytes(Long fileAssetId, String url) {
        try {
            if (fileAssetId != null) {
                var fa = fileAssetsRepository.findById(fileAssetId).orElse(null);
                if (fa != null && fa.getPath() != null && !fa.getPath().isBlank()) {
                    Path p = Paths.get(fa.getPath()).toAbsolutePath().normalize();
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        return Files.readAllBytes(p);
                    }
                }
            }

            String prefix = urlPrefix == null ? "/uploads" : urlPrefix.trim();
            String u = blankToNull(url);
            if (u == null || prefix.isEmpty()) return null;
            if (!u.startsWith(prefix + "/")) return null;

            int q = u.indexOf('?');
            if (q >= 0) u = u.substring(0, q);
            String rel = u.substring(prefix.length());
            while (rel.startsWith("/")) rel = rel.substring(1);

            Path root = Paths.get(uploadRoot == null ? "uploads" : uploadRoot).toAbsolutePath().normalize();
            Path p = root.resolve(rel).normalize();
            if (!p.startsWith(root)) return null;
            if (!Files.exists(p) || !Files.isRegularFile(p)) return null;
            return Files.readAllBytes(p);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String appendImagesAsText(String prompt, List<ImageRef> images) {
        String base = prompt == null ? "" : prompt;
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n[IMAGES]\n");
        int take = 0;
        for (ImageRef img : images) {
            if (img == null) continue;
            String url = img.url() == null ? null : img.url().trim();
            if (url == null || url.isBlank()) continue;
            sb.append("- ").append(url).append('\n');
            take += 1;
            if (take >= 5) break;
        }
        return sb.toString().trim();
    }

    private List<ImageRef> resolveImages(LlmModerationTestRequest req) {
        if (req == null) return List.of();
        List<ImageRef> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (req.getImages() != null) {
            for (LlmModerationTestRequest.ImageInput in : req.getImages()) {
                if (in == null) continue;
                String u = blankToNull(in.getUrl());
                if (u == null) continue;
                String mt = blankToNull(in.getMimeType());
                boolean isImg = mt != null && mt.toLowerCase(Locale.ROOT).startsWith("image/");
                if (!isImg && !isLikelyImageUrl(u)) continue;
                if (seen.contains(u)) continue;
                seen.add(u);
                out.add(new ImageRef(in.getFileAssetId(), u, mt));
                if (out.size() >= 5) break;
            }
        }
        if (!out.isEmpty()) return out;

        if (req.getQueueId() == null) return List.of();
        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null) return List.of();
        if (q.getContentType() != ContentType.POST) return List.of();

        try {
            var page = postAttachmentsRepository.findByPostId(
                    q.getContentId(),
                    PageRequest.of(0, 50, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
            );
            if (page == null || page.getContent() == null) return List.of();
            for (var a : page.getContent()) {
                if (a == null) continue;
                String u = blankToNull(a.getUrl());
                if (u == null) continue;
                String mt = a.getMimeType() == null ? "" : a.getMimeType().trim().toLowerCase(Locale.ROOT);
                if (!mt.startsWith("image/")) continue;
                if (seen.contains(u)) continue;
                seen.add(u);
                out.add(new ImageRef(a.getFileAssetId(), u, mt));
                if (out.size() >= 5) break;
            }
            return out.isEmpty() ? List.of() : out;
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private PromptVars resolvePromptVarsSafe(LlmModerationTestRequest req) {
        if (req == null) return null;
        if (req.getText() != null && !req.getText().isBlank()) {
            String content = req.getText();
            return new PromptVars("", content, content);
        }
        if (req.getQueueId() == null) return null;

        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null) return null;

        if (q.getContentType() == ContentType.POST) {
            var p = postsRepository.findById(q.getContentId()).orElse(null);
            if (p == null) return null;
            String title = p.getTitle() == null ? "" : p.getTitle();
            String content = p.getContent() == null ? "" : p.getContent();
            String base = ("[POST]\n标题: " + title + "\n内容: " + content).trim();
            String reports = buildReportsBlock(ReportTargetType.POST, q.getContentId());
            String text = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + base).trim() : base;
            String contentWithReports = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + content).trim() : content;
            return new PromptVars(title, contentWithReports, text);
        }
        if (q.getContentType() == ContentType.COMMENT) {
            var c = commentsRepository.findById(q.getContentId()).orElse(null);
            if (c == null) return null;
            String content = c.getContent() == null ? "" : c.getContent();
            String base = ("[COMMENT]\n内容: " + content).trim();
            String reports = buildReportsBlock(ReportTargetType.COMMENT, q.getContentId());
            String text = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + base).trim() : base;
            String contentWithReports = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + content).trim() : content;
            return new PromptVars("", contentWithReports, text);
        }

        return null;
    }

    private PromptVars resolvePromptVars(LlmModerationTestRequest req) {
        if (req == null) return null;
        if (req.getText() != null && !req.getText().isBlank()) {
            String content = req.getText();
            return new PromptVars("", content, content);
        }
        if (req.getQueueId() == null) return null;

        ModerationQueueEntity q = queueRepository.findById(req.getQueueId()).orElse(null);
        if (q == null) return null;

        if (q.getContentType() == ContentType.POST) {
            var p = postsRepository.findById(q.getContentId()).orElse(null);
            if (p == null) return null;
            String title = p.getTitle() == null ? "" : p.getTitle();
            String content = p.getContent() == null ? "" : p.getContent();
            String base = ("[POST]\n标题: " + title + "\n内容: " + content).trim();
            String reports = buildReportsBlock(ReportTargetType.POST, q.getContentId());
            String text = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + base).trim() : base;
            String contentWithReports = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + content).trim() : content;
            return new PromptVars(title, contentWithReports, text);
        }
        if (q.getContentType() == ContentType.COMMENT) {
            var c = commentsRepository.findById(q.getContentId()).orElse(null);
            if (c == null) return null;
            String content = c.getContent() == null ? "" : c.getContent();
            String base = ("[COMMENT]\n内容: " + content).trim();
            String reports = buildReportsBlock(ReportTargetType.COMMENT, q.getContentId());
            String text = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + base).trim() : base;
            String contentWithReports = (reports != null && !reports.isBlank()) ? (reports + "\n\n" + content).trim() : content;
            return new PromptVars("", contentWithReports, text);
        }

        return null;
    }

    private String buildReportsBlock(ReportTargetType targetType, Long targetId) {
        if (targetType == null || targetId == null) return null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0,
                10,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("id"))
        );
        var page = reportsRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) return null;

        List<String> lines = new ArrayList<>();
        for (var r : page.getContent()) {
            if (r == null) continue;
            if (r.getStatus() != ReportStatus.PENDING && r.getStatus() != ReportStatus.REVIEWING) continue;
            String code = r.getReasonCode() == null ? "" : r.getReasonCode().trim();
            String text = r.getReasonText() == null ? "" : r.getReasonText().trim();
            if (code.isEmpty() && text.isEmpty()) continue;
            if (!text.isEmpty()) {
                lines.add("- " + code + ": " + text);
            } else {
                lines.add("- " + code);
            }
            if (lines.size() >= 3) break;
        }
        if (lines.isEmpty()) return null;
        return ("[REPORTS]\n" + String.join("\n", lines)).trim();
    }

    private ModerationLlmConfigEntity merge(ModerationLlmConfigEntity base, LlmModerationTestRequest.LlmModerationConfigOverrideDTO o) {
        ModerationLlmConfigEntity m = new ModerationLlmConfigEntity();
        m.setId(base.getId());
        m.setPromptTemplate(base.getPromptTemplate());
        m.setVisionPromptTemplate(base.getVisionPromptTemplate());
        m.setModel(base.getModel());
        m.setProviderId(base.getProviderId());
        m.setVisionModel(base.getVisionModel());
        m.setVisionProviderId(base.getVisionProviderId());
        m.setTemperature(base.getTemperature());
        m.setVisionTemperature(base.getVisionTemperature());
        m.setMaxTokens(base.getMaxTokens());
        m.setVisionMaxTokens(base.getVisionMaxTokens());
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
        if (o.getVisionPromptTemplate() != null) m.setVisionPromptTemplate(o.getVisionPromptTemplate());
        if (o.getModel() != null) m.setModel(o.getModel());
        if (o.getProviderId() != null) m.setProviderId(o.getProviderId());
        if (o.getVisionModel() != null) m.setVisionModel(o.getVisionModel());
        if (o.getVisionProviderId() != null) m.setVisionProviderId(o.getVisionProviderId());
        if (o.getTemperature() != null) m.setTemperature(o.getTemperature());
        if (o.getVisionTemperature() != null) m.setVisionTemperature(o.getVisionTemperature());
        if (o.getMaxTokens() != null) m.setMaxTokens(o.getMaxTokens());
        if (o.getVisionMaxTokens() != null) m.setVisionMaxTokens(o.getVisionMaxTokens());
        if (o.getThreshold() != null) m.setThreshold(o.getThreshold());
        if (o.getAutoRun() != null) m.setAutoRun(o.getAutoRun());
        if (o.getMaxConcurrent() != null) m.setMaxConcurrent(o.getMaxConcurrent());
        if (o.getMinDelayMs() != null) m.setMinDelayMs(o.getMinDelayMs());
        if (o.getQps() != null) m.setQps(o.getQps());
        return m;
    }

    private ModerationLlmConfigEntity defaultEntity() {
        ModerationLlmConfigEntity e = new ModerationLlmConfigEntity();
        e.setPromptTemplate("");
        e.setVisionPromptTemplate(defaultVisionPromptTemplate());
        e.setModel(null);
        e.setProviderId(null);
        e.setVisionModel(null);
        e.setVisionProviderId(null);
        e.setTemperature(0.2);
        e.setVisionTemperature(0.2);
        e.setMaxTokens(null);
        e.setVisionMaxTokens(null);
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
        dto.setVisionPromptTemplate(e.getVisionPromptTemplate());
        dto.setModel(e.getModel());
        dto.setProviderId(e.getProviderId());
        dto.setVisionModel(e.getVisionModel());
        dto.setVisionProviderId(e.getVisionProviderId());
        dto.setTemperature(e.getTemperature());
        dto.setVisionTemperature(e.getVisionTemperature());
        dto.setMaxTokens(e.getMaxTokens());
        dto.setVisionMaxTokens(e.getVisionMaxTokens());
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
            String reason = textOrNull(root.path("reason"));
            if (reason != null && !reason.isBlank() && !out.reasons.contains(reason)) {
                out.reasons.add(reason);
            }

            out.riskTags = new ArrayList<>();
            JsonNode riskTags = root.path("riskTags");
            if (riskTags.isArray()) {
                for (JsonNode n : riskTags) {
                    if (n.isTextual()) out.riskTags.add(n.asText());
                }
            }
            JsonNode labels = root.path("labels");
            if (labels.isArray()) {
                for (JsonNode n : labels) {
                    if (!n.isTextual()) continue;
                    String tag = n.asText();
                    if (tag == null || tag.isBlank()) continue;
                    if (!out.riskTags.contains(tag)) out.riskTags.add(tag);
                }
            } else if (labels.isTextual()) {
                String tag = labels.asText();
                if (tag != null && !tag.isBlank() && !out.riskTags.contains(tag)) out.riskTags.add(tag);
            }

            out.description = textOrNull(root.path("description"));
            if (out.description == null || out.description.isBlank()) {
                out.description = textOrNull(root.path("imageDescription"));
            }

            // normalize
            if (out.decision == null || out.decision.isBlank()) {
                Boolean safe = booleanOrNull(root.path("safe"));
                if (safe != null) {
                    out.decision = safe ? "APPROVE" : "REJECT";
                }
            }
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

    private static Boolean booleanOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isBoolean()) return n.asBoolean();
        if (n.isTextual()) {
            String t = n.asText();
            if (t == null) return null;
            String s = t.trim().toLowerCase(Locale.ROOT);
            if (s.equals("true") || s.equals("yes") || s.equals("y") || s.equals("1")) return Boolean.TRUE;
            if (s.equals("false") || s.equals("no") || s.equals("n") || s.equals("0")) return Boolean.FALSE;
        }
        return null;
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
        String description;
    }
}

