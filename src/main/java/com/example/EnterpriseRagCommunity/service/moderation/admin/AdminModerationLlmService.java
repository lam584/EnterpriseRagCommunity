package com.example.EnterpriseRagCommunity.service.moderation.admin;

import java.time.LocalDateTime;
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
        if (req == null) throw new IllegalArgumentException("request cannot be null");
        boolean useQueue = !Boolean.FALSE.equals(req.getUseQueue());

        ModerationLlmConfigEntity base = configSupport.loadBaseConfigCached();
        ModerationLlmConfigEntity merged = configSupport.merge(base, req.getConfigOverride());

        PromptVars vars = contextBuilder.resolvePromptVarsSafe(req);
        String text = vars == null ? null : vars.text();
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text cannot be empty (or queueId cannot resolve content)");
        if (text.length() > 6000) {
            text = text.substring(0, 6000);
            vars = new PromptVars(vars.title(), vars.content(), text);
        }

        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = req.getConfigOverride();

        PromptsEntity textPromptEntity = promptsRepository.findByPromptCode(merged.getTextPromptCode())
            .orElseThrow(() -> new IllegalStateException("Text prompt not found: " + merged.getTextPromptCode()));
        PromptsEntity visionPromptEntity = promptsRepository.findByPromptCode(merged.getVisionPromptCode())
            .orElseThrow(() -> new IllegalStateException("Vision prompt not found: " + merged.getVisionPromptCode()));

        int maxImages = imageSupport.clampVisionMaxImages(visionPromptEntity.getVisionMaxImagesPerRequest());
        List<ImageRef> images = imageSupport.resolveImages(req, maxImages);

        String promptTemplate = (override != null && override.getPromptTemplate() != null && !override.getPromptTemplate().isBlank())
            ? override.getPromptTemplate()
            : textPromptEntity.getUserPromptTemplate();
        String baseSystemPrompt = (override != null && override.getSystemPrompt() != null)
            ? override.getSystemPrompt()
            : textPromptEntity.getSystemPrompt();

        String visionPromptTemplate = (override != null && override.getVisionPromptTemplate() != null && !override.getVisionPromptTemplate().isBlank())
            ? override.getVisionPromptTemplate()
            : visionPromptEntity.getUserPromptTemplate();
        String baseVisionSystemPrompt = (override != null && override.getVisionSystemPrompt() != null)
            ? override.getVisionSystemPrompt()
            : visionPromptEntity.getSystemPrompt();

        if (promptTemplate == null || promptTemplate.isBlank()) {
            throw new IllegalStateException("Text moderation prompt template is not configured");
        }

        PromptLlmParams textInvoke = resolveTextPromptInvocation(textPromptEntity);
        PromptLlmParams visionInvoke = resolveVisionPromptInvocation(visionPromptEntity);

        String systemPrompt = baseSystemPrompt == null ? "" : baseSystemPrompt.trim();
        String visionSystemPrompt = baseVisionSystemPrompt == null ? "" : baseVisionSystemPrompt.trim();
        String trace = contextBuilder.buildQueueTraceLine(req);
        if (trace != null && !trace.isBlank()) {
            String t = trace.trim();
            systemPrompt = systemPrompt.isBlank() ? t : (systemPrompt + "\n" + t);
            visionSystemPrompt = visionSystemPrompt.isBlank() ? t : (visionSystemPrompt + "\n" + t);
        }
        String policyBlock = contextBuilder.buildPolicyContextBlock(req, useQueue);
        if (policyBlock != null && !policyBlock.isBlank()) {
            String t = policyBlock.trim();
            systemPrompt = systemPrompt.isBlank() ? t : (systemPrompt + "\n" + t);
            visionSystemPrompt = visionSystemPrompt.isBlank() ? t : (visionSystemPrompt + "\n" + t);
        }

        ModerationConfidenceFallbackConfigEntity fb = loadFallbackRequired();
        double textRiskThreshold = require01(fb.getLlmTextRiskThreshold(), "llmTextRiskThreshold");
        double imageRiskThreshold = require01(fb.getLlmImageRiskThreshold(), "llmImageRiskThreshold");
        double strongRejectThreshold = require01(fb.getLlmStrongRejectThreshold(), "llmStrongRejectThreshold");
        double strongPassThreshold = require01(fb.getLlmStrongPassThreshold(), "llmStrongPassThreshold");
        double judgeThreshold = require01(fb.getLlmCrossModalThreshold(), "llmCrossModalThreshold");
        double rejectThreshold = require01(fb.getLlmRejectThreshold(), "llmRejectThreshold");
        double humanThreshold = require01(fb.getLlmHumanThreshold(), "llmHumanThreshold");
        if (humanThreshold > rejectThreshold) humanThreshold = rejectThreshold;

        // Resolve tag thresholds map
        Map<String, Double> tagThresholds = resolveTagThresholds();
        LlmModerationTestResponse.LabelTaxonomy labelTaxonomy = resolveRiskLabelTaxonomy();

        String allowedLabelsHint = null;
        if (labelTaxonomy != null && labelTaxonomy.getAllowedLabels() != null && !labelTaxonomy.getAllowedLabels().isEmpty()) {
            String joined = String.join(", ", labelTaxonomy.getAllowedLabels());
            if (joined.length() > 1200) joined = joined.substring(0, 1200);
            allowedLabelsHint = "label_taxonomy.allowed_labels (labels/riskTags must come from this list): " + joined;
        }
        if (allowedLabelsHint != null && !allowedLabelsHint.isBlank()) {
            systemPrompt = systemPrompt.isBlank() ? allowedLabelsHint : (systemPrompt + "\n" + allowedLabelsHint);
            visionSystemPrompt = visionSystemPrompt.isBlank() ? allowedLabelsHint : (visionSystemPrompt + "\n" + allowedLabelsHint);
        }

        QueueCtx ctx = contextBuilder.resolveQueueCtx(req, useQueue);
        String textInputJson = contextBuilder.buildTextAuditInputJson(req, vars, ctx);
        String textPrompt0 = AdminModerationLlmUpstreamSupport.renderTextPrompt(promptTemplate, vars);
        String textPrompt = AdminModerationLlmUpstreamSupport.mergePromptAndJson(promptTemplate, textPrompt0, textInputJson);
        String visionInputJsonList = contextBuilder.buildVisionAuditInputJsonList(req, ctx, images);

        if (images == null || images.isEmpty()) {
                StageCallResult one0 = upstreamSupport.callTextOnce(
                    systemPrompt,
                    textPrompt,
                    textInvoke.temperature(),
                    textInvoke.topP(),
                    textInvoke.maxTokens(),
                    textInvoke.providerId(),
                    textInvoke.model(),
                    textInvoke.enableThinking(),
                    useQueue
                );
            StageCallResult one = enforceRiskTagsWhitelist(one0, labelTaxonomy);
            LlmModerationTestResponse resp = new LlmModerationTestResponse();
            resp.setDecisionSuggestion(one.decisionSuggestion());
            resp.setDecision(one.decision());
            resp.setRiskScore(one.riskScore());
            resp.setScore(one.score());
            resp.setReasons(one.reasons());
            resp.setLabels(one.labels());
            resp.setRiskTags(one.riskTags());
            resp.setLabelTaxonomy(labelTaxonomy);
            resp.setSeverity(one.severity());
            resp.setUncertainty(one.uncertainty());
            resp.setEvidence(upstreamSupport.enrichEvidenceWithText(one.evidence(), text));
            resp.setRawModelOutput(one.rawModelOutput());
            resp.setModel(one.model());
            resp.setLatencyMs(one.latencyMs());
            resp.setUsage(one.usage());
            resp.setPromptMessages(one.promptMessages());
            resp.setImages(List.of());
            resp.setInputMode(one.inputMode());

            if ((resp.getDecision() == null || resp.getDecision().isBlank()) && resp.getScore() != null) {
                double s = clamp01(resp.getScore(), 0.0);
                if (s >= rejectThreshold) resp.setDecision("REJECT");
                else if (s >= humanThreshold) resp.setDecision("HUMAN");
                else resp.setDecision("APPROVE");
            }
            if (resp.getDecisionSuggestion() == null || resp.getDecisionSuggestion().isBlank()) {
                resp.setDecisionSuggestion(AdminModerationLlmUpstreamSupport.decisionToSuggestion(resp.getDecision()));
            }
            if (resp.getRiskScore() == null) resp.setRiskScore(resp.getScore());
            if (resp.getLabels() == null) resp.setLabels(List.of());
            return resp;
        }

        List<String> urls = new ArrayList<>();
        for (ImageRef img : images) {
            if (img == null) continue;
            if (img.url() == null || img.url().isBlank()) continue;
            urls.add(img.url().trim());
            if (urls.size() >= maxImages) break;
        }

        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();

        StageCallResult textStage = enforceRiskTagsWhitelist(
            upstreamSupport.callTextOnce(
                systemPrompt,
                textPrompt,
                textInvoke.temperature(),
                textInvoke.topP(),
                textInvoke.maxTokens(),
                textInvoke.providerId(),
                textInvoke.model(),
                textInvoke.enableThinking(),
                useQueue
            ),
                labelTaxonomy
        );
        stages.setText(toStage(textStage, null));
        if (isStageCallFailed(textStage)) {
            return finalizeMultiStage("HUMAN", textStage == null ? null : textStage.score(), List.of("Text moderation output invalid, routed to HUMAN"), textStage == null ? null : textStage.riskTags(), stages, urls, labelTaxonomy, textStage);
        }

        Double textScore = textStage.score();
        double ts = textScore == null ? 0.0 : clamp01(textScore, 0.0);
        
        boolean textHitTag = exceedsTagThreshold(ts, textStage.riskTags(), tagThresholds);
        String textDecision = (ts >= textRiskThreshold || textHitTag) ? "REJECT" : "APPROVE";
        if (stages.getText() != null) stages.getText().setDecision(textDecision);

        if (ts >= strongRejectThreshold) {
            List<String> reasons = new ArrayList<>();
        reasons.add("Strong reject: text high confidence violation (>= " + strongRejectThreshold + ")");
            return finalizeMultiStage("REJECT", ts, reasons, textStage.riskTags(), stages, urls, labelTaxonomy, textStage);
        }

        if (visionPromptTemplate == null || visionPromptTemplate.isBlank()) {
            throw new IllegalStateException("Vision moderation prompt template is not configured");
        }

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
                visionPromptEntity.getVisionImageTokenBudget(),
                visionPromptEntity.getVisionMaxImagesPerRequest(),
                visionPromptEntity.getVisionHighResolutionImages(),
                visionPromptEntity.getVisionMaxPixels(),
                useQueue
        );
        StageCallResult imageStage = enforceRiskTagsWhitelist(imageStage0, labelTaxonomy);
        stages.setImage(toStage(imageStage, imageStage == null ? null : imageStage.description()));
        if (isStageCallFailed(imageStage)) {
            return finalizeMultiStage("HUMAN", imageStage == null ? null : imageStage.score(), List.of("Image moderation output invalid, routed to HUMAN"), imageStage == null ? null : imageStage.riskTags(), stages, urls, labelTaxonomy, imageStage);
        }

        Double imageScore = imageStage == null ? null : imageStage.score();
        double is = imageScore == null ? 0.0 : clamp01(imageScore, 0.0);

        boolean imageHitTag = exceedsTagThreshold(is, imageStage == null ? null : imageStage.riskTags(), tagThresholds);
        String imageDecision = (is >= imageRiskThreshold || imageHitTag) ? "REJECT" : "APPROVE";
        if (stages.getImage() != null) stages.getImage().setDecision(imageDecision);

        if (ts >= strongRejectThreshold || is >= strongRejectThreshold) {
            List<String> reasons = new ArrayList<>();
            reasons.add("Strong reject: high confidence violation exists (>= " + strongRejectThreshold + ")");
            return finalizeMultiStage("REJECT", Math.max(ts, is), reasons, mergeTags(textStage.riskTags(), imageStage == null ? null : imageStage.riskTags()), stages, urls, labelTaxonomy, imageStage);
        }

        if (ts < strongPassThreshold && is < strongPassThreshold) {
            List<String> reasons = new ArrayList<>();
            reasons.add("Strong pass: both text and image are low risk (< " + strongPassThreshold + ")");
            return finalizeMultiStage("APPROVE", Math.max(ts, is), reasons, mergeTags(textStage.riskTags(), imageStage == null ? null : imageStage.riskTags()), stages, urls, labelTaxonomy, imageStage);
        }

        PromptsEntity judgePromptEntity = promptsRepository.findByPromptCode(merged.getJudgePromptCode())
            .orElseThrow(() -> new IllegalStateException("Judge prompt not found: " + merged.getJudgePromptCode()));
        PromptLlmParams judgeInvoke = resolveTextPromptInvocation(judgePromptEntity);
        String judgeTpl = (override != null && override.getJudgePromptTemplate() != null && !override.getJudgePromptTemplate().isBlank())
            ? override.getJudgePromptTemplate()
            : judgePromptEntity.getUserPromptTemplate();

        if (judgeTpl == null || judgeTpl.isBlank()) {
            throw new IllegalStateException("Judge prompt template is not configured");
        }
        String judgePrompt = null;
        if (ctx != null && ctx.queue() != null && imageStage != null) {
            judgePrompt = contextBuilder.buildJudgeInputJson(
                    ctx,
                    text,
                    imageStage.description(),
                    ts,
                    is,
                    textStage.reasons(),
                    imageStage.reasons(),
                    textStage.evidence(),
                    imageStage.evidence(),
                    ctx.queue().getContentType().name(),
                    ctx.queue().getContentId()
            );
        }
        if (judgePrompt == null) {
            judgePrompt = AdminModerationLlmUpstreamSupport.renderJudgePrompt(judgeTpl, text, imageStage == null ? null : imageStage.description(), ts, is, textStage.reasons(), imageStage == null ? null : imageStage.reasons());
        }
        StageCallResult judgeStage = enforceRiskTagsWhitelist(
                upstreamSupport.callTextOnce(
                        systemPrompt,
                        judgePrompt,
                        judgeInvoke.temperature(),
                        judgeInvoke.topP(),
                        judgeInvoke.maxTokens(),
                        judgeInvoke.providerId(),
                        judgeInvoke.model(),
                        judgeInvoke.enableThinking(),
                        useQueue
                ),
                labelTaxonomy
        );
        stages.setJudge(toStage(judgeStage, null));

        if (isStageCallFailed(judgeStage)) {
            return finalizeMultiStage(
                    "HUMAN",
                    judgeStage == null ? null : judgeStage.score(),
                    List.of("Judge output invalid, routed to HUMAN"),
                    mergeTags(mergeTags(textStage.riskTags(), imageStage == null ? null : imageStage.riskTags()), judgeStage == null ? null : judgeStage.riskTags()),
                    stages,
                    urls,
                    labelTaxonomy,
                    judgeStage
            );
        }

        double js = judgeStage.score() == null ? 0.0 : clamp01(judgeStage.score(), 0.0);
        String finalDecision = judgeStage.decision();
        
        boolean judgeHitTag = exceedsTagThreshold(js, judgeStage.riskTags(), tagThresholds);
        
        if (finalDecision == null || finalDecision.isBlank() || (!"APPROVE".equalsIgnoreCase(finalDecision) && !"REJECT".equalsIgnoreCase(finalDecision) && !"HUMAN".equalsIgnoreCase(finalDecision))) {
            finalDecision = (js >= judgeThreshold || judgeHitTag) ? "REJECT" : "APPROVE";
        }
        if ("REJECT".equalsIgnoreCase(finalDecision) && js < judgeThreshold && !judgeHitTag) {
            finalDecision = "APPROVE";
        }
        if ("APPROVE".equalsIgnoreCase(finalDecision) && (js >= judgeThreshold || judgeHitTag)) {
            finalDecision = "REJECT";
        }

        StageCallResult finalStageForReturn = judgeStage;
        boolean conflict = ("REJECT".equalsIgnoreCase(textDecision) && "APPROVE".equalsIgnoreCase(imageDecision))
                || ("APPROVE".equalsIgnoreCase(textDecision) && "REJECT".equalsIgnoreCase(imageDecision));
        Map<String, Object> thresholds = fb.getThresholds();
        boolean upgradeEnable = asBooleanRequired(thresholds == null ? null : thresholds.get("llm.cross.upgrade.enable"), "llm.cross.upgrade.enable");
        boolean upgradeOnConflict = asBooleanRequired(thresholds == null ? null : thresholds.get("llm.cross.upgrade.onConflict"), "llm.cross.upgrade.onConflict");
        boolean upgradeOnUncertainty = asBooleanRequired(thresholds == null ? null : thresholds.get("llm.cross.upgrade.onUncertainty"), "llm.cross.upgrade.onUncertainty");
        boolean upgradeOnGray = asBooleanRequired(thresholds == null ? null : thresholds.get("llm.cross.upgrade.onGray"), "llm.cross.upgrade.onGray");
        double uncertaintyMin = clamp01Strict(asDoubleRequired(thresholds == null ? null : thresholds.get("llm.cross.upgrade.uncertaintyMin"), "llm.cross.upgrade.uncertaintyMin"));
        double scoreGrayMargin = clamp01Strict(asDoubleRequired(thresholds == null ? null : thresholds.get("llm.cross.upgrade.scoreGrayMargin"), "llm.cross.upgrade.scoreGrayMargin"));
        double judgeUn = judgeStage.uncertainty() == null ? 0.0 : clamp01(judgeStage.uncertainty(), 0.0);
        boolean shouldUpgrade = upgradeEnable && (
                (upgradeOnConflict && conflict)
                        || (upgradeOnUncertainty && judgeUn >= uncertaintyMin)
                        || (upgradeOnGray && Math.abs(js - judgeThreshold) <= scoreGrayMargin)
        );
        if (shouldUpgrade) {
            PromptLlmParams judgeUpgradeInvoke = judgeInvoke;
            String upgradeTpl = (override != null && override.getJudgePromptTemplate() != null && !override.getJudgePromptTemplate().isBlank())
                ? override.getJudgePromptTemplate()
                : judgePromptEntity.getUserPromptTemplate();

            if (upgradeTpl == null || upgradeTpl.isBlank()) {
                throw new IllegalStateException("Judge upgrade prompt template is not configured");
            }
            String upgradePrompt = AdminModerationLlmUpstreamSupport.renderJudgeUpgradePrompt(
                    upgradeTpl,
                    text,
                    imageStage == null ? null : imageStage.description(),
                    ts,
                    is,
                    js,
                    judgeThreshold,
                    textStage.reasons(),
                    imageStage == null ? null : imageStage.reasons(),
                    judgeStage.reasons(),
                    textStage.evidence(),
                    imageStage == null ? null : imageStage.evidence(),
                    judgeStage.evidence()
            );
            StageCallResult upgradeStage = enforceRiskTagsWhitelist(
                    upstreamSupport.callTextOnce(
                            systemPrompt,
                            upgradePrompt,
                            judgeUpgradeInvoke.temperature(),
                            judgeUpgradeInvoke.topP(),
                            judgeUpgradeInvoke.maxTokens(),
                            judgeUpgradeInvoke.providerId(),
                            judgeUpgradeInvoke.model(),
                            judgeUpgradeInvoke.enableThinking(),
                            useQueue
                    ),
                    labelTaxonomy
            );
            stages.setUpgrade(toStage(upgradeStage, null));
            if (isStageCallFailed(upgradeStage)) {
                return finalizeMultiStage(
                        "HUMAN",
                        upgradeStage == null ? null : upgradeStage.score(),
                        List.of("Judge upgrade output invalid, routed to HUMAN"),
                        mergeTags(mergeTags(textStage.riskTags(), imageStage == null ? null : imageStage.riskTags()), judgeStage.riskTags()),
                        stages,
                        urls,
                        labelTaxonomy,
                        upgradeStage
                );
            }
            double us = upgradeStage.score() == null ? js : clamp01(upgradeStage.score(), js);
            double uu = upgradeStage.uncertainty() == null ? 0.0 : clamp01(upgradeStage.uncertainty(), 0.0);
            if (uu >= uncertaintyMin) {
                return finalizeMultiStage("HUMAN", us, List.of("Judge upgrade: uncertainty too high, routed to HUMAN"), mergeTags(mergeTags(textStage.riskTags(), imageStage == null ? null : imageStage.riskTags()), judgeStage.riskTags()), stages, urls, labelTaxonomy, upgradeStage);
            }
            String d = upgradeStage.decision();
            if (d == null || d.isBlank() || (!"APPROVE".equalsIgnoreCase(d) && !"REJECT".equalsIgnoreCase(d) && !"HUMAN".equalsIgnoreCase(d))) {
                d = us >= judgeThreshold ? "REJECT" : "APPROVE";
            }
            finalDecision = d.toUpperCase(Locale.ROOT);
            js = us;
            finalStageForReturn = upgradeStage;
        }

        List<String> finalReasons = new ArrayList<>();
        finalReasons.add("Judge threshold decision based on threshold=" + judgeThreshold);
        if (judgeStage.reasons() != null && !judgeStage.reasons().isEmpty()) finalReasons.addAll(judgeStage.reasons());
        if (stages.getUpgrade() != null && stages.getUpgrade().getReasons() != null && !stages.getUpgrade().getReasons().isEmpty()) {
            finalReasons.add("Judge upgrade stage applied");
            finalReasons.addAll(stages.getUpgrade().getReasons());
        }

        return finalizeMultiStage(finalDecision.toUpperCase(Locale.ROOT), js, finalReasons, mergeTags(mergeTags(textStage.riskTags(), imageStage == null ? null : imageStage.riskTags()), judgeStage.riskTags()), stages, urls, labelTaxonomy, finalStageForReturn);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LlmModerationTestResponse testImageOnly(LlmModerationTestRequest req) {
        if (req == null) throw new IllegalArgumentException("request cannot be null");
        boolean useQueue = !Boolean.FALSE.equals(req.getUseQueue());

        ModerationLlmConfigEntity base = configSupport.loadBaseConfigCached();
        ModerationLlmConfigEntity merged = configSupport.merge(base, req.getConfigOverride());

        PromptVars vars = contextBuilder.resolvePromptVarsSafe(req);
        if (vars == null) vars = new PromptVars("", "", "");

        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = req.getConfigOverride();
        PromptsEntity visionPromptEntity = promptsRepository.findByPromptCode(merged.getVisionPromptCode())
            .orElseThrow(() -> new IllegalStateException("Vision prompt not found: " + merged.getVisionPromptCode()));
        PromptLlmParams visionInvoke = resolveVisionPromptInvocation(visionPromptEntity);

        int maxImages = imageSupport.clampVisionMaxImages(visionPromptEntity.getVisionMaxImagesPerRequest());
        List<ImageRef> images = imageSupport.resolveImages(req, maxImages);
        if (images == null || images.isEmpty()) return null;

        String visionPromptTemplate = (override != null && override.getVisionPromptTemplate() != null && !override.getVisionPromptTemplate().isBlank())
            ? override.getVisionPromptTemplate()
            : visionPromptEntity.getUserPromptTemplate();
        String baseVisionSystemPrompt = (override != null && override.getVisionSystemPrompt() != null)
            ? override.getVisionSystemPrompt()
            : visionPromptEntity.getSystemPrompt();

        if (visionPromptTemplate == null || visionPromptTemplate.isBlank()) {
            throw new IllegalStateException("Vision moderation prompt template is not configured");
        }
        String visionSystemPrompt = baseVisionSystemPrompt == null ? "" : baseVisionSystemPrompt.trim();
        String trace = contextBuilder.buildQueueTraceLine(req);
        if (trace != null && !trace.isBlank()) {
            String t = trace.trim();
            visionSystemPrompt = visionSystemPrompt.isBlank() ? t : (visionSystemPrompt + "\n" + t);
        }

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
                visionPromptEntity.getVisionImageTokenBudget(),
                visionPromptEntity.getVisionMaxImagesPerRequest(),
                visionPromptEntity.getVisionHighResolutionImages(),
                visionPromptEntity.getVisionMaxPixels(),
                useQueue
        );
        StageCallResult imageStage = enforceRiskTagsWhitelist(imageStage0, labelTaxonomy);
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
        if (v == null) throw new IllegalStateException("missing threshold: " + key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")) return false;
        throw new IllegalStateException("invalid boolean threshold: " + key);
    }

    private static double asDoubleRequired(Object v, String key) {
        if (v == null) throw new IllegalStateException("missing threshold: " + key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new IllegalStateException("invalid double threshold: " + key, e);
        }
    }

    private static double clamp01Strict(double v) {
        if (!Double.isFinite(v)) throw new IllegalStateException("invalid double value");
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
        resp.setInputMode("multistage");
        if (finalStage != null) {
            resp.setSeverity(finalStage.severity());
            resp.setUncertainty(finalStage.uncertainty());
            resp.setEvidence(finalStage.evidence());
            resp.setRawModelOutput(finalStage.rawModelOutput());
            resp.setModel(finalStage.model());
            resp.setLatencyMs(finalStage.latencyMs());
            resp.setUsage(finalStage.usage());
            resp.setPromptMessages(finalStage.promptMessages());
        } else if (stages != null && stages.getJudge() != null) {
            resp.setSeverity(stages.getJudge().getSeverity());
            resp.setUncertainty(stages.getJudge().getUncertainty());
            resp.setEvidence(stages.getJudge().getEvidence());
            resp.setRawModelOutput(stages.getJudge().getRawModelOutput());
            resp.setModel(stages.getJudge().getModel());
            resp.setLatencyMs(stages.getJudge().getLatencyMs());
            resp.setUsage(stages.getJudge().getUsage());
        } else if (stages != null && stages.getImage() != null) {
            resp.setSeverity(stages.getImage().getSeverity());
            resp.setUncertainty(stages.getImage().getUncertainty());
            resp.setEvidence(stages.getImage().getEvidence());
            resp.setRawModelOutput(stages.getImage().getRawModelOutput());
            resp.setModel(stages.getImage().getModel());
            resp.setLatencyMs(stages.getImage().getLatencyMs());
            resp.setUsage(stages.getImage().getUsage());
        } else if (stages != null && stages.getText() != null) {
            resp.setSeverity(stages.getText().getSeverity());
            resp.setUncertainty(stages.getText().getUncertainty());
            resp.setEvidence(stages.getText().getEvidence());
            resp.setRawModelOutput(stages.getText().getRawModelOutput());
            resp.setModel(stages.getText().getModel());
            resp.setLatencyMs(stages.getText().getLatencyMs());
            resp.setUsage(stages.getText().getUsage());
        }
        return resp;
    }
}





