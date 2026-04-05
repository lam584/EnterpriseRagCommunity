package com.example.EnterpriseRagCommunity.service.moderation.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.PromptLlmParams;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationTagSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationThresholdSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminModerationLlmService {

    private final ModerationConfidenceFallbackConfigRepository fallbackRepository;
    private final TagsRepository tagsRepository;
    private final PromptsRepository promptsRepository;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;
    private final AdminModerationLlmImageSupport imageSupport;
    private final AdminModerationLlmContextBuilder contextBuilder;
    private final AdminModerationLlmConfigSupport configSupport;
    private final AdminModerationLlmUpstreamSupport upstreamSupport;

    @Transactional(readOnly = true)
    public LlmModerationConfigDTO getConfig() {
        ModerationLlmConfigEntity cfg = configSupport.loadBaseConfigCached();
        return configSupport.toDto(cfg, null);
    }

    @Transactional
    public LlmModerationConfigDTO upsertConfig(LlmModerationConfigDTO payload, Long actorUserId, String actorUsername) {
        ConfigUpsertResult r = configSupport.upsertConfigEntity(payload, actorUserId);
        ModerationLlmConfigEntity cfg = r.saved();

        try {
            Map<String, Object> diff = auditDiffBuilder.build(r.beforeSummary(), r.afterSummary());
            auditLogWriter.write(
                    actorUserId,
                    actorUsername,
                    "CONFIG_CHANGE",
                    "MODERATION_LLM_CONFIG",
                    cfg.getId(),
                    com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS,
                    "Updated LLM moderation config",
                    null,
                    diff
            );
        } catch (Exception ignore) {
        }

        return configSupport.toDto(cfg, actorUsername);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LlmModerationTestResponse test(LlmModerationTestRequest req) {
        TestRequestContext requestContext = resolveTestRequestContext(req);
        boolean useQueue = !Boolean.FALSE.equals(req.getUseQueue());
        ModerationLlmConfigEntity merged = requestContext.merged();
        PromptVars vars = requestContext.vars();
        String text = vars == null ? null : vars.text();
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text cannot be empty (or queueId cannot resolve content)");
        if (text.length() > 6000) {
            text = text.substring(0, 6000);
            vars = new PromptVars(vars.title(), vars.content(), text);
        }

        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = req.getConfigOverride();

        PromptsEntity multimodalPromptEntity = promptsRepository.findByPromptCode(merged.getMultimodalPromptCode())
            .orElseThrow(() -> new IllegalStateException("Multimodal prompt not found: " + merged.getMultimodalPromptCode()));

        int maxImages = imageSupport.clampVisionMaxImages(multimodalPromptEntity.getVisionMaxImagesPerRequest());
        List<ImageRef> images = imageSupport.resolveImages(req, maxImages);

        String visionPromptTemplate = (override != null && override.getVisionPromptTemplate() != null && !override.getVisionPromptTemplate().isBlank())
            ? override.getVisionPromptTemplate()
            : multimodalPromptEntity.getUserPromptTemplate();
        String baseVisionSystemPrompt = (override != null && override.getVisionSystemPrompt() != null)
            ? override.getVisionSystemPrompt()
            : multimodalPromptEntity.getSystemPrompt();

        if (visionPromptTemplate == null || visionPromptTemplate.isBlank()) {
            throw new IllegalStateException("Multimodal moderation prompt template is not configured");
        }

        PromptLlmParams visionInvoke = resolveVisionPromptInvocation(multimodalPromptEntity);

        String visionSystemPrompt = appendPromptLine(baseVisionSystemPrompt, contextBuilder.buildQueueTraceLine(req));
        String policyBlock = contextBuilder.buildPolicyContextBlock(req, useQueue);
        visionSystemPrompt = appendPromptLine(visionSystemPrompt, policyBlock);

        ModerationConfidenceFallbackConfigEntity fb = loadFallbackRequired();
        double rejectThreshold = require01(fb.getLlmRejectThreshold(), "llmRejectThreshold");
        double humanThreshold = require01(fb.getLlmHumanThreshold(), "llmHumanThreshold");
        if (humanThreshold > rejectThreshold) humanThreshold = rejectThreshold;

        // Resolve tag thresholds map
        Map<String, Double> tagThresholds = resolveTagThresholds();
        LlmModerationTestResponse.LabelTaxonomy labelTaxonomy = resolveRiskLabelTaxonomy();

        String allowedLabelsHint = null;
        if (labelTaxonomy.getAllowedLabels() != null && !labelTaxonomy.getAllowedLabels().isEmpty()) {
            String joined = String.join(", ", labelTaxonomy.getAllowedLabels());
            if (joined.length() > 1200) joined = joined.substring(0, 1200);
            allowedLabelsHint = "label_taxonomy.allowed_labels (labels/riskTags must come from this list): " + joined;
        }
        if (allowedLabelsHint != null && !allowedLabelsHint.isBlank()) {
            visionSystemPrompt = visionSystemPrompt.isBlank() ? allowedLabelsHint : (visionSystemPrompt + "\n" + allowedLabelsHint);
        }

        QueueCtx ctx = contextBuilder.resolveQueueCtx(req, useQueue);
        String visionInputJsonList = contextBuilder.buildVisionAuditInputJsonList(req, ctx, images);

        List<String> urls = new ArrayList<>();
        for (ImageRef img : images) {
            if (img == null) continue;
            if (img.url() == null || img.url().isBlank()) continue;
            urls.add(img.url().trim());
            if (urls.size() >= maxImages) break;
        }

        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();

        double textRejectThreshold = clamp01(fb.getLlmTextRiskThreshold(), rejectThreshold);
        String textPrompt = AdminModerationLlmUpstreamSupport.renderTextPrompt(visionPromptTemplate, vars);
        StageCallResult textStage0 = upstreamSupport.callTextOnce(
            visionSystemPrompt,
            textPrompt,
            visionInvoke.temperature(),
            visionInvoke.topP(),
            visionInvoke.maxTokens(),
            visionInvoke.providerId(),
            visionInvoke.model(),
            visionInvoke.enableThinking(),
            useQueue
        );
        StageCallResult textStage = enforceRiskTagsWhitelist(textStage0, labelTaxonomy);
        stages.setText(toStage(textStage, textStage == null ? null : textStage.description()));
        if (isStageCallFailed(textStage)) {
            return finalizeMultiStage(
                "HUMAN",
                textStage == null ? null : textStage.score(),
                List.of("Text moderation output invalid, routed to HUMAN"),
                textStage == null ? null : textStage.riskTags(),
                stages,
                List.of(),
                labelTaxonomy,
                textStage
            );
        }

        Double textScore = textStage.score();
        double ts = textScore == null ? 0.0 : clamp01(textScore, 0.0);
        boolean textHitTag = exceedsTagThreshold(ts, textStage.riskTags(), tagThresholds);
        String textDecision = resolveStageDecision(textStage, textRejectThreshold, humanThreshold, textHitTag);
        stages.getText().setDecision(textDecision);

        if (images.isEmpty() || "REJECT".equalsIgnoreCase(textDecision)) {
            List<String> finalReasons = textStage.reasons() == null
                ? new ArrayList<>()
                : new ArrayList<>(textStage.reasons());
            if (finalReasons.isEmpty()) {
            finalReasons.add("Text moderation decision finalized from primary stage output");
            }
            return finalizeMultiStage(
                    textDecision.toUpperCase(Locale.ROOT),
                ts,
                finalReasons,
                textStage.riskTags(),
                stages,
                List.of(),
                labelTaxonomy,
                textStage
            );
        }

        StageCallResult imageStage = callVisionStage(
                visionSystemPrompt,
                vars,
                images,
                visionPromptTemplate,
                visionInputJsonList,
                visionInvoke,
                multimodalPromptEntity,
                useQueue,
                labelTaxonomy
        );
        stages.setImage(toStage(imageStage, imageStage == null ? null : imageStage.description()));
        if (isStageCallFailed(imageStage)) {
            return finalizeMultiStage("HUMAN", imageStage == null ? null : imageStage.score(), List.of("Multimodal moderation output invalid, routed to HUMAN"), imageStage == null ? null : imageStage.riskTags(), stages, urls, labelTaxonomy, imageStage);
        }

        Double imageScore = imageStage.score();
        double is = imageScore == null ? 0.0 : clamp01(imageScore, 0.0);

        boolean imageHitTag = exceedsTagThreshold(is, imageStage.riskTags(), tagThresholds);
        String imageDecision = imageStage.decision();
        if (imageDecision == null || imageDecision.isBlank()) {
            if (imageHitTag || is >= rejectThreshold) imageDecision = "REJECT";
            else if (is >= humanThreshold) imageDecision = "HUMAN";
            else imageDecision = "APPROVE";
        }
        stages.getImage().setDecision(imageDecision);

        String finalDecision = combineStageDecision(textDecision, imageDecision);
        double finalScore = Math.max(ts, is);
        List<String> finalReasons = new ArrayList<>();
        if (textStage.reasons() != null) finalReasons.addAll(textStage.reasons());
        if (imageStage.reasons() != null) {
            for (String reason : imageStage.reasons()) {
                if (reason != null && !reason.isBlank() && !finalReasons.contains(reason)) {
                    finalReasons.add(reason);
                }
            }
        }
        if (finalReasons.isEmpty()) {
            finalReasons.add("Multimodal moderation decision finalized from stage aggregation");
        }
        StageCallResult decisiveStage = resolveDecisiveStage(finalDecision, textDecision, imageDecision, textStage, imageStage);
        return finalizeMultiStage(
                finalDecision,
                finalScore,
                finalReasons,
                mergeTags(textStage.riskTags(), imageStage.riskTags()),
                stages,
                urls,
                labelTaxonomy,
                decisiveStage
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LlmModerationTestResponse testImageOnly(LlmModerationTestRequest req) {
        TestRequestContext requestContext = resolveTestRequestContext(req);
        boolean useQueue = !Boolean.FALSE.equals(req.getUseQueue());
        ModerationLlmConfigEntity merged = requestContext.merged();
        PromptVars vars = requestContext.vars();
        if (vars == null) vars = new PromptVars("", "", "");

        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = req.getConfigOverride();
        PromptsEntity multimodalPromptEntity = promptsRepository.findByPromptCode(merged.getMultimodalPromptCode())
            .orElseThrow(() -> new IllegalStateException("Multimodal prompt not found: " + merged.getMultimodalPromptCode()));
        PromptLlmParams visionInvoke = resolveVisionPromptInvocation(multimodalPromptEntity);

        int maxImages = imageSupport.clampVisionMaxImages(multimodalPromptEntity.getVisionMaxImagesPerRequest());
        List<ImageRef> images = imageSupport.resolveImages(req, maxImages);
        if (images == null || images.isEmpty()) return null;

        String visionPromptTemplate = (override != null && override.getVisionPromptTemplate() != null && !override.getVisionPromptTemplate().isBlank())
            ? override.getVisionPromptTemplate()
            : multimodalPromptEntity.getUserPromptTemplate();
        String baseVisionSystemPrompt = (override != null && override.getVisionSystemPrompt() != null)
            ? override.getVisionSystemPrompt()
            : multimodalPromptEntity.getSystemPrompt();

        if (visionPromptTemplate == null || visionPromptTemplate.isBlank()) {
            throw new IllegalStateException("Vision moderation prompt template is not configured");
        }
        String visionSystemPrompt = appendPromptLine(baseVisionSystemPrompt, contextBuilder.buildQueueTraceLine(req));

        ModerationConfidenceFallbackConfigEntity fb = loadFallbackRequired();
        double imageRiskThreshold = clamp01(fb.getLlmImageRiskThreshold(), 0.30);
        
        // Resolve tag thresholds map
        Map<String, Double> tagThresholds = resolveTagThresholds();
        LlmModerationTestResponse.LabelTaxonomy labelTaxonomy = resolveRiskLabelTaxonomy();

        List<String> urls = new ArrayList<>();
        for (ImageRef img : images) {
            if (img == null) continue;
            if (img.url() == null || img.url().isBlank()) continue;
            urls.add(img.url().trim());
            if (urls.size() >= 5) break;
        }

        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();
        QueueCtx ctx = contextBuilder.resolveQueueCtx(req, useQueue);
        String visionInputJsonList = contextBuilder.buildVisionAuditInputJsonList(req, ctx, images);
        StageCallResult imageStage = callVisionStage(
                visionSystemPrompt,
                vars,
                images,
                visionPromptTemplate,
                visionInputJsonList,
                visionInvoke,
                multimodalPromptEntity,
                useQueue,
                labelTaxonomy
        );
        stages.setImage(toStage(imageStage, imageStage == null ? null : imageStage.description()));

        if (imageStage != null && "HUMAN".equalsIgnoreCase(imageStage.decision())) {
            return finalizeMultiStage("HUMAN", null, List.of("Image moderation failed, routed to HUMAN"), List.of("UPSTREAM_ERROR"), stages, urls, labelTaxonomy, imageStage);
        }

        double is = imageStage == null || imageStage.score() == null ? 0.0 : clamp01(imageStage.score(), 0.0);
        boolean imageHitTag = exceedsTagThreshold(is, imageStage == null ? null : imageStage.riskTags(), tagThresholds);
        String imageDecision = (is >= imageRiskThreshold || imageHitTag) ? "REJECT" : "APPROVE";
        if (stages.getImage() != null) stages.getImage().setDecision(imageDecision);

        List<String> reasons = imageStage == null || imageStage.reasons() == null ? List.of() : imageStage.reasons();
        List<String> riskTags = imageStage == null || imageStage.riskTags() == null ? List.of() : imageStage.riskTags();
        return finalizeMultiStage(imageDecision, is, reasons, riskTags, stages, urls, labelTaxonomy, imageStage);
    }

    private static PromptLlmParams resolveTextPromptInvocation(PromptsEntity prompt) {
        if (prompt == null) throw new IllegalStateException("prompt not found for text invocation");
        String providerId = AdminModerationLlmConfigSupport.blankToNull(prompt.getProviderId());
        String model = AdminModerationLlmConfigSupport.blankToNull(prompt.getModelName());
        Double temperature = prompt.getTemperature();
        Double topP = prompt.getTopP();
        Integer maxTokens = prompt.getMaxTokens();
        Boolean enableThinking = prompt.getEnableDeepThinking() != null ? prompt.getEnableDeepThinking() : Boolean.FALSE;
        return new PromptLlmParams(providerId, model, temperature, topP, maxTokens, enableThinking);
    }

    private StageCallResult callVisionStage(
            String visionSystemPrompt,
            PromptVars vars,
            List<ImageRef> images,
            String visionPromptTemplate,
            String visionInputJsonList,
            PromptLlmParams visionInvoke,
            PromptsEntity multimodalPromptEntity,
            boolean useQueue,
            LlmModerationTestResponse.LabelTaxonomy labelTaxonomy
    ) {
        StageCallResult imageStage0 = upstreamSupport.callImageDescribeOnce(
                visionSystemPrompt,
                vars,
                images,
                visionPromptTemplate,
                visionInputJsonList,
                visionInvoke.temperature(),
                visionInvoke.topP(),
                visionInvoke.maxTokens(),
                visionInvoke.providerId(),
                visionInvoke.model(),
                visionInvoke.enableThinking(),
                multimodalPromptEntity.getVisionImageTokenBudget(),
                multimodalPromptEntity.getVisionMaxImagesPerRequest(),
                multimodalPromptEntity.getVisionHighResolutionImages(),
                multimodalPromptEntity.getVisionMaxPixels(),
                useQueue
        );
        return enforceRiskTagsWhitelist(imageStage0, labelTaxonomy);
    }

    private static PromptLlmParams resolveVisionPromptInvocation(PromptsEntity prompt) {
        if (prompt == null) throw new IllegalStateException("prompt not found for vision invocation");
        String providerId = AdminModerationLlmConfigSupport.blankToNull(
                prompt.getVisionProviderId() != null ? prompt.getVisionProviderId() : prompt.getProviderId()
        );
        String model = AdminModerationLlmConfigSupport.blankToNull(
                prompt.getVisionModel() != null ? prompt.getVisionModel() : prompt.getModelName()
        );
        Double temperature = prompt.getVisionTemperature() != null ? prompt.getVisionTemperature() : prompt.getTemperature();
        Double topP = prompt.getVisionTopP() != null ? prompt.getVisionTopP() : prompt.getTopP();
        Integer maxTokens = prompt.getVisionMaxTokens() != null ? prompt.getVisionMaxTokens() : prompt.getMaxTokens();
        Boolean enableThinking = prompt.getVisionEnableDeepThinking() != null
                ? prompt.getVisionEnableDeepThinking()
                : (prompt.getEnableDeepThinking() != null ? prompt.getEnableDeepThinking() : Boolean.FALSE);
        return new PromptLlmParams(providerId, model, temperature, topP, maxTokens, enableThinking);
    }

    private Map<String, Double> resolveTagThresholds() {
        Map<String, Double> map = new HashMap<>();
        try {
            List<TagsEntity> tags = tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK);
            for (TagsEntity t : tags) {
                if (t != null && t.getThreshold() != null) {
                    if (t.getSlug() != null && !t.getSlug().isBlank()) {
                        map.put(t.getSlug(), t.getThreshold());
                    }
                    if (t.getName() != null && !t.getName().isBlank()) {
                        map.put(t.getName(), t.getThreshold());
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return map;
    }

    private LlmModerationTestResponse.LabelTaxonomy resolveRiskLabelTaxonomy() {
        LlmModerationTestResponse.LabelTaxonomy out = new LlmModerationTestResponse.LabelTaxonomy();
        out.setTaxonomyId("risk_tags");
        List<TagsEntity> rows;
        try {
            rows = tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK);
        } catch (Exception e) {
            rows = List.of();
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        List<LlmModerationTestResponse.LabelItem> items = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (TagsEntity t : rows) {
            if (t == null) continue;
            String slug = t.getSlug();
            if (slug == null || slug.isBlank()) continue;
            String name = t.getName();
            if (name == null || name.isBlank()) name = slug;
            String key = slug + "\u0000" + name;
            if (seen.contains(key)) continue;
            seen.add(key);
            LlmModerationTestResponse.LabelItem it = new LlmModerationTestResponse.LabelItem();
            it.setSlug(slug);
            it.setName(name);
            items.add(it);
            allowed.add(name);
        }
        out.setAllowedLabels(new ArrayList<>(allowed));
        out.setLabelMap(items);
        return out;
    }

    static StageCallResult enforceRiskTagsWhitelist(StageCallResult r, LlmModerationTestResponse.LabelTaxonomy tax) {
        if (r == null) return null;
        if (tax == null || tax.getAllowedLabels() == null || tax.getAllowedLabels().isEmpty()) return r;

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String it : tax.getAllowedLabels()) {
            if (it != null && !it.isBlank()) allowed.add(it.trim());
        }
        Map<String, String> slugToName = new LinkedHashMap<>();
        if (tax.getLabelMap() != null) {
            for (LlmModerationTestResponse.LabelItem it : tax.getLabelMap()) {
                if (it == null) continue;
                String slug = it.getSlug();
                String name = it.getName();
                if (slug == null || slug.isBlank()) continue;
                if (name == null || name.isBlank()) continue;
                slugToName.put(slug.trim().toLowerCase(Locale.ROOT), name.trim());
                allowed.add(name.trim());
            }
        }

        List<String> raw = r.riskTags() == null ? List.of() : r.riskTags();
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String tag : raw) {
            if (tag == null) continue;
            String t = tag.trim();
            if (t.isBlank()) continue;
            if (allowed.contains(t)) {
                filtered.add(t);
                continue;
            }
            String mapped = slugToName.get(t.toLowerCase(Locale.ROOT));
            if (mapped != null) filtered.add(mapped);
        }

        if (raw.isEmpty()) return r;
        List<String> next = new ArrayList<>(filtered);
        if (next.size() == raw.size()) return r;

        List<String> reasons = r.reasons() == null ? new ArrayList<>() : new ArrayList<>(r.reasons());
        reasons.add("Filtered out risk tags not present in risk tags whitelist");
        String suggestion = r.decisionSuggestion();
        if (suggestion != null && suggestion.equalsIgnoreCase("REJECT") && next.isEmpty()) {
            suggestion = "ESCALATE";
        }
        String decision = r.decision();
        if (decision != null && decision.equalsIgnoreCase("REJECT") && next.isEmpty()) {
            decision = "HUMAN";
        }

        return new StageCallResult(
                suggestion,
                r.riskScore(),
                next,
                decision,
                r.score(),
                reasons,
                next,
                r.severity(),
                r.uncertainty(),
                r.evidence(),
                r.rawModelOutput(),
                r.model(),
                r.latencyMs(),
                r.usage(),
                r.promptMessages(),
                r.description(),
                r.inputMode()
        );
    }

    private boolean exceedsTagThreshold(Double score, List<String> labels, Map<String, Double> thresholds) {
        if (score == null || labels == null || thresholds == null || thresholds.isEmpty()) return false;
        for (String label : labels) {
            if (label == null) continue;
            Double t = thresholds.get(label);
            if (t != null && score >= t) return true;
        }
        return false;
    }

    private ModerationConfidenceFallbackConfigEntity loadFallbackRequired() {
        ModerationConfidenceFallbackConfigEntity fb = fallbackRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"))
                .stream()
                .findFirst()
                .orElse(null);
        if (fb == null) throw new IllegalStateException("moderation_confidence_fallback_config not initialized");
        return fb;
    }

    private static double require01(Double v, String key) {
        if (v == null || !Double.isFinite(v)) throw new IllegalStateException("missing config: " + key);
        if (v < 0 || v > 1) throw new IllegalStateException("invalid config range: " + key);
        return v;
    }

    private static double clamp01(Double v, double def) {
        if (v == null || !Double.isFinite(v)) return def;
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    private static boolean asBooleanRequired(Object v, String key) {
        return com.example.EnterpriseRagCommunity.service.moderation.ModerationThresholdSupport.asBooleanRequired(v, key);
    }

    private static double asDoubleRequired(Object v, String key) {
        return ModerationThresholdSupport.asDoubleRequired(v, key);
    }

    private static double clamp01Strict(double v) {
        if (!Double.isFinite(v)) throw new IllegalStateException("invalid double value");
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    private static String resolveStageDecision(StageCallResult stage, double rejectThreshold, double humanThreshold, boolean tagThresholdHit) {
        String decision = stage == null ? null : stage.decision();
        if (decision != null && !decision.isBlank()) {
            return decision.trim().toUpperCase(Locale.ROOT);
        }
        double score = clamp01(stage == null ? null : stage.score(), 0.0);
        if (tagThresholdHit || score >= rejectThreshold) return "REJECT";
        if (score >= humanThreshold) return "HUMAN";
        return "APPROVE";
    }

    private static String combineStageDecision(String left, String right) {
        if ("REJECT".equalsIgnoreCase(left) || "REJECT".equalsIgnoreCase(right)) return "REJECT";
        if ("HUMAN".equalsIgnoreCase(left) || "HUMAN".equalsIgnoreCase(right)) return "HUMAN";
        if ("APPROVE".equalsIgnoreCase(left) || "APPROVE".equalsIgnoreCase(right)) return "APPROVE";
        return "HUMAN";
    }

    private static StageCallResult resolveDecisiveStage(
            String finalDecision,
            String textDecision,
            String imageDecision,
            StageCallResult textStage,
            StageCallResult imageStage
    ) {
        if ("REJECT".equalsIgnoreCase(finalDecision)) {
            if ("REJECT".equalsIgnoreCase(textDecision)) return textStage;
            if ("REJECT".equalsIgnoreCase(imageDecision)) return imageStage;
        }
        if ("HUMAN".equalsIgnoreCase(finalDecision)) {
            if ("HUMAN".equalsIgnoreCase(textDecision)) return textStage;
            if ("HUMAN".equalsIgnoreCase(imageDecision)) return imageStage;
        }
        return imageStage != null ? imageStage : textStage;
    }

    private static String appendPromptLine(String prompt, String line) {
        if (line == null || line.isBlank()) return prompt == null ? "" : prompt;
        String base = prompt == null ? "" : prompt.trim();
        String value = line.trim();
        return base.isBlank() ? value : (base + "\n" + value);
    }

    private static List<String> mergeTags(List<String> a, List<String> b) {
        return ModerationTagSupport.mergeTags(a, b);
    }

    private static boolean isStageCallFailed(StageCallResult r) {
        if (r == null) return true;
        List<String> tags = r.riskTags();
        if (tags != null) {
            for (String t : tags) {
                if (t == null) continue;
                String s = t.trim();
                if (s.equalsIgnoreCase("PARSE_ERROR")) return true;
                if (s.equalsIgnoreCase("UPSTREAM_ERROR")) return true;
                if (s.equalsIgnoreCase("PROVIDER_OUTPUT_BLOCKED")) return true;
            }
        }
        List<String> reasons = r.reasons();
        if (reasons != null) {
            for (String it : reasons) {
                if (it == null) continue;
                String s = it.trim().toLowerCase(Locale.ROOT);
                if (s.contains("could not be parsed as json")) return true;
                if (s.contains("upstream")) return true;
            }
        }
        return false;
    }

    private static LlmModerationTestResponse.Stage toStage(StageCallResult r, String desc) {
        if (r == null) return null;
        LlmModerationTestResponse.Stage s = new LlmModerationTestResponse.Stage();
        s.setDecisionSuggestion(r.decisionSuggestion());
        s.setDecision(r.decision());
        s.setRiskScore(r.riskScore());
        s.setScore(r.score());
        s.setReasons(r.reasons());
        s.setLabels(r.labels());
        s.setRiskTags(r.riskTags());
        s.setSeverity(r.severity());
        s.setUncertainty(r.uncertainty());
        s.setEvidence(r.evidence());
        s.setRawModelOutput(r.rawModelOutput());
        s.setModel(r.model());
        s.setLatencyMs(r.latencyMs());
        s.setUsage(r.usage());
        s.setDescription(desc);
        s.setInputMode(r.inputMode());
        return s;
    }

    private static void applyStageMetadata(LlmModerationTestResponse target, StageCallResult source) {
        if (target == null || source == null) return;
        target.setSeverity(source.severity());
        target.setUncertainty(source.uncertainty());
        target.setEvidence(source.evidence());
        target.setRawModelOutput(source.rawModelOutput());
        target.setModel(source.model());
        target.setLatencyMs(source.latencyMs());
        target.setUsage(source.usage());
    }

    private static void applyStageMetadata(LlmModerationTestResponse target, LlmModerationTestResponse.Stage source) {
        if (target == null || source == null) return;
        target.setSeverity(source.getSeverity());
        target.setUncertainty(source.getUncertainty());
        target.setEvidence(source.getEvidence());
        target.setRawModelOutput(source.getRawModelOutput());
        target.setModel(source.getModel());
        target.setLatencyMs(source.getLatencyMs());
        target.setUsage(source.getUsage());
    }

    private TestRequestContext resolveTestRequestContext(LlmModerationTestRequest req) {
        if (req == null) throw new IllegalArgumentException("request cannot be null");
        ModerationLlmConfigEntity base = configSupport.loadBaseConfigCached();
        ModerationLlmConfigEntity merged = configSupport.merge(base, req.getConfigOverride());
        PromptVars vars = contextBuilder.resolvePromptVarsSafe(req);
        return new TestRequestContext(merged, vars);
    }

    private record TestRequestContext(
            ModerationLlmConfigEntity merged,
            PromptVars vars
    ) {
    }

    private LlmModerationTestResponse finalizeMultiStage(
            String decision,
            Double score,
            List<String> reasons,
            List<String> riskTags,
            LlmModerationTestResponse.Stages stages,
            List<String> images
    ) {
        return finalizeMultiStage(decision, score, reasons, riskTags, stages, images, null, null);
    }

    private LlmModerationTestResponse finalizeMultiStage(
            String decision,
            Double score,
            List<String> reasons,
            List<String> riskTags,
            LlmModerationTestResponse.Stages stages,
            List<String> images,
            LlmModerationTestResponse.LabelTaxonomy labelTaxonomy,
            StageCallResult finalStage
    ) {
        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setDecisionSuggestion(AdminModerationLlmUpstreamSupport.decisionToSuggestion(decision));
        resp.setDecision(decision);
        resp.setRiskScore(score);
        resp.setScore(score);
        resp.setReasons(reasons);
        List<String> labels = null;
        if (finalStage != null) labels = finalStage.labels();
        if (labels == null && stages != null && stages.getJudge() != null) labels = stages.getJudge().getLabels();
        if (labels == null && stages != null && stages.getImage() != null) labels = stages.getImage().getLabels();
        if (labels == null && stages != null && stages.getText() != null) labels = stages.getText().getLabels();
        resp.setLabels(labels == null ? List.of() : labels);
        resp.setRiskTags(riskTags == null ? List.of() : riskTags);
        resp.setLabelTaxonomy(labelTaxonomy);
        resp.setStages(stages);
        resp.setImages(images == null ? List.of() : images);
        boolean hasSecondaryStage = stages != null && (stages.getJudge() != null || stages.getUpgrade() != null || stages.getText() != null);
        resp.setInputMode(hasSecondaryStage ? "multistage" : "multimodal");
        if (finalStage != null) {
            applyStageMetadata(resp, finalStage);
            resp.setPromptMessages(finalStage.promptMessages());
        } else if (stages != null && stages.getJudge() != null) {
            applyStageMetadata(resp, stages.getJudge());
        } else if (stages != null && stages.getImage() != null) {
            applyStageMetadata(resp, stages.getImage());
        } else if (stages != null && stages.getText() != null) {
            applyStageMetadata(resp, stages.getText());
        }
        return resp;
    }
}





