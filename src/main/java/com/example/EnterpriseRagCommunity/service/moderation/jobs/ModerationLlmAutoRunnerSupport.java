package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAnchorSnippetSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkImageSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationCollectionSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationFallbackDecisionService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationMapPathSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationThresholdSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationViolationSnippetSupport;
import com.example.EnterpriseRagCommunity.service.retrieval.RagValueSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ModerationLlmAutoRunnerSupport {

    private static final java.util.regex.Pattern IMAGE_PLACEHOLDER = java.util.regex.Pattern.compile("\\[\\[IMAGE_(\\d+)]]");
    private static final ObjectMapper EVIDENCE_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP_TYPE = new TypeReference<>() {
    };

    private ModerationLlmAutoRunnerSupport() {
    }

    static final class PolicyEval {
        final Verdict verdict;
        final Map<String, Object> details;

        PolicyEval(Verdict verdict, Map<String, Object> details) {
            this.verdict = verdict;
            this.details = details;
        }

        Verdict verdict() {
            return verdict;
        }

        Map<String, Object> details() {
            return details;
        }
    }

    static final class Thresholds {
        final double tAllow;
        final double tReject;
        final String source;

        Thresholds(double tAllow, double tReject, String source) {
            this.tAllow = tAllow;
            this.tReject = tReject;
            this.source = source;
        }

        double tAllow() {
            return tAllow;
        }

        double tReject() {
            return tReject;
        }

        String source() {
            return source;
        }
    }

    static final class ChunkedVerdict {
        final Verdict verdict;
        final Thresholds thresholds;

        ChunkedVerdict(Verdict verdict, Thresholds thresholds) {
            this.verdict = verdict;
            this.thresholds = thresholds;
        }

        Verdict verdict() {
            return verdict;
        }

        Thresholds thresholds() {
            return thresholds;
        }
    }

    static PolicyEval evaluatePolicyVerdict(
            Map<String, Object> policyConfig,
            String reviewStage,
            LlmModerationTestResponse res,
            Map<String, Double> tagThresholds,
            boolean upgraded,
            boolean upgradeFailed
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (reviewStage != null) details.put("reviewStage", reviewStage);

        if (upgraded && upgradeFailed) {
            details.put("reason", "upgrade_failed");
            return new PolicyEval(Verdict.REVIEW, details);
        }

        String suggestion = normalizeSuggestion(res == null ? null : res.getDecisionSuggestion(), res == null ? null : res.getDecision());
        Double rs0 = res == null ? null : (res.getRiskScore() == null ? res.getScore() : res.getRiskScore());
        double riskScore = rs0 == null ? 0.0 : clamp01(rs0);
        List<String> labels = coalesceLabels(res);
        details.put("decision_suggestion", suggestion);
        details.put("risk_score", riskScore);
        if (labels != null && !labels.isEmpty()) details.put("labels", labels);

        if ("ESCALATE".equalsIgnoreCase(suggestion)) {
            details.put("reason", "suggested_escalate");
            return new PolicyEval(Verdict.REVIEW, details);
        }

        Thresholds th = resolveThresholdsRequired(policyConfig, reviewStage, labels);
        details.put("thresholdsEffective", Map.of("T_allow", th.tAllow, "T_reject", th.tReject));
        details.put("thresholdSource", th.source);

        boolean tagThresholdHit = exceedsTagThreshold(riskScore, res == null ? null : res.getRiskTags(), tagThresholds);
        if (tagThresholdHit) details.put("tagThresholdHit", true);

        boolean requireEvidence = asBooleanOrDefault(deepGet(policyConfig, "escalate_rules.require_evidence"), false);
        boolean evidenceMissing = res == null || res.getEvidence() == null || res.getEvidence().isEmpty();
        details.put("requireEvidence", requireEvidence);
        details.put("evidenceMissing", evidenceMissing);

        Verdict verdict;
        if ("REJECT".equalsIgnoreCase(suggestion) || tagThresholdHit) {
            verdict = Verdict.REJECT;
        } else if ("ALLOW".equalsIgnoreCase(suggestion)) {
            if (riskScore >= th.tReject) verdict = Verdict.REJECT;
            else if (riskScore <= th.tAllow) verdict = Verdict.APPROVE;
            else verdict = Verdict.REVIEW;
        } else {
            if (riskScore >= th.tReject) verdict = Verdict.REJECT;
            else if (riskScore <= th.tAllow) verdict = Verdict.APPROVE;
            else verdict = Verdict.REVIEW;
        }

        if (verdict == Verdict.REJECT) {
            if ("reported".equalsIgnoreCase(reviewStage) && evidenceMissing) {
                details.put("reason", "reported_requires_evidence");
                verdict = Verdict.REVIEW;
            } else if (requireEvidence && evidenceMissing) {
                details.put("reason", "requires_evidence");
                verdict = Verdict.REVIEW;
            }
        }

        if (verdict == Verdict.REVIEW && details.get("reason") == null) {
            details.put("reason", "threshold_gray_zone");
        }
        return new PolicyEval(verdict, details);
    }

    static boolean exceedsTagThreshold(double score, List<String> riskTags, Map<String, Double> thresholds) {
        if (riskTags == null || riskTags.isEmpty() || thresholds == null || thresholds.isEmpty()) return false;
        for (String tag : riskTags) {
            if (tag == null || tag.isBlank()) continue;
            Double t = thresholds.get(tag.trim());
            if (t != null && score >= t) return true;
        }
        return false;
    }

    @SuppressWarnings("SameParameterValue")
    static Thresholds resolveThresholdsRequired(Map<String, Object> policyConfig, String reviewStage, List<String> labels) {
        // Keep signature aligned with caller contract where label-aware thresholds may be introduced later.
        double tAllow = clamp01Strict(asDoubleRequired(deepGet(policyConfig, "thresholds.default.T_allow"), "thresholds.default.T_allow"));
        double tReject = clamp01Strict(asDoubleRequired(deepGet(policyConfig, "thresholds.default.T_reject"), "thresholds.default.T_reject"));
        String source = "policy.default";

        if (reviewStage != null && !reviewStage.isBlank()) {
            String s = reviewStage.trim();
            Object oa = deepGet(policyConfig, "thresholds.by_review_stage." + s + ".T_allow");
            Object or = deepGet(policyConfig, "thresholds.by_review_stage." + s + ".T_reject");
            if (oa != null)
                tAllow = clamp01Strict(asDoubleRequired(oa, "thresholds.by_review_stage." + s + ".T_allow"));
            if (or != null)
                tReject = clamp01Strict(asDoubleRequired(or, "thresholds.by_review_stage." + s + ".T_reject"));
            if (oa != null || or != null) source = "policy.by_review_stage";
        }

        if (tAllow > tReject) tAllow = tReject;
        return new Thresholds(tAllow, tReject, source);
    }

    static String resolveReviewStage(com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity q) {
        if (q == null) return null;
        String candidate = q.getReviewStage();
        if (candidate == null || candidate.isBlank()) {
            candidate = (q.getCaseType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.REPORT) ? "reported" : null;
        }
        return normalizeReviewStage(candidate);
    }

    static String normalizeReviewStage(String reviewStage) {
        if (reviewStage == null) return null;
        String s = reviewStage.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        if ("reported".equals(s) || "appeal".equals(s) || "default".equals(s)) return s;
        return null;
    }

    static List<String> coalesceLabels(LlmModerationTestResponse res) {
        if (res == null) return List.of();
        if (res.getLabels() != null && !res.getLabels().isEmpty()) return res.getLabels();
        if (res.getRiskTags() != null && !res.getRiskTags().isEmpty()) return res.getRiskTags();
        return List.of();
    }

    static boolean hasIntersection(List<String> a, List<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return false;
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (String s : a) {
            if (s != null && !s.isBlank()) set.add(s.trim());
        }
        for (String s : b) {
            if (s != null && !s.isBlank() && set.contains(s.trim())) return true;
        }
        return false;
    }

    static String normalizeSuggestion(String suggestion, String decisionFallback) {
        String raw = (suggestion == null || suggestion.isBlank()) ? decisionFallback : suggestion;
        if (raw == null) return "ESCALATE";
        String d = raw.trim().toUpperCase(Locale.ROOT);
        switch (d) {
            case "ALLOW", "REJECT", "ESCALATE" -> {
                return d;
            }
        }
        if (d.equals("APPROVE")) return "ALLOW";
        if (d.equals("HUMAN")) return "ESCALATE";
        if (raw.toLowerCase(Locale.ROOT).contains("allow")) return "ALLOW";
        if (raw.toLowerCase(Locale.ROOT).contains("reject")) return "REJECT";
        if (raw.toLowerCase(Locale.ROOT).contains("escalate")) return "ESCALATE";
        return d;
    }

    static List<String> asStringList(Object v) {
        return ModerationCollectionSupport.asStringList(v);
    }

    static Object deepGet(Map<String, Object> root, String path) {
        return ModerationMapPathSupport.deepGet(root, path);
    }

    static Map<String, Object> asMap(Object v) {
        return ModerationMapPathSupport.asMap(v);
    }

    static Verdict verdictFromLlm(LlmModerationTestResponse res, ModerationConfidenceFallbackConfigEntity fb) {
        String decision = res == null ? null : ModerationFallbackDecisionService.normalizeDecision(res.getDecision());
        if ("APPROVE".equals(decision)) return Verdict.APPROVE;
        if ("REJECT".equals(decision)) return Verdict.REJECT;
        if ("HUMAN".equals(decision)) return Verdict.REVIEW;
        return ModerationFallbackDecisionService.verdictFromLlmScore(
                res == null ? null : res.getScore(),
                fb == null ? null : fb.getLlmRejectThreshold(),
                fb == null ? null : fb.getLlmHumanThreshold()
        );
    }

    static ChunkedVerdict verdictFromLlmInChunked(
            LlmModerationTestResponse res,
            ModerationConfidenceFallbackConfigEntity fb,
            Map<String, Object> policyConfig,
            String reviewStage
    ) {
        String decision = res == null ? null : ModerationFallbackDecisionService.normalizeDecision(res.getDecision());
        Thresholds th = resolveThresholdsPreferred(policyConfig, reviewStage, coalesceLabels(res), fb);
        if ("APPROVE".equals(decision)) return new ChunkedVerdict(Verdict.APPROVE, th);
        if ("REJECT".equals(decision)) return new ChunkedVerdict(Verdict.REJECT, th);
        if ("HUMAN".equals(decision)) return new ChunkedVerdict(Verdict.REVIEW, th);
        Verdict v = ModerationFallbackDecisionService.verdictFromLlmScore(
                res == null ? null : res.getScore(),
                th.tReject,
                th.tAllow
        );
        return new ChunkedVerdict(v, th);
    }

    static Verdict aggregateChunkVerdict(
            com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO progress,
            ModerationConfidenceFallbackConfigEntity fb,
            Map<String, Object> policyConfig,
            String reviewStage,
            List<String> labels
    ) {
        boolean anyHuman = false;
        if (progress == null || progress.getChunks() == null) return Verdict.REVIEW;
        Thresholds th = resolveThresholdsPreferred(policyConfig, reviewStage, labels, fb);
        double rejectThreshold = th.tReject;
        double humanThreshold = th.tAllow;

        for (var c : progress.getChunks()) {
            if (c == null) continue;
            String v = c.getVerdict();
            Double s = c.getScore();
            if (s != null && Double.isFinite(s)) {
                double sc = s;
                if (sc < 0) sc = 0;
                if (sc > 1) sc = 1;
                if (sc >= rejectThreshold) return Verdict.REJECT;
                if (sc >= humanThreshold) anyHuman = true;
            } else {
                if ("REJECT".equalsIgnoreCase(v)) return Verdict.REJECT;
                if ("REVIEW".equalsIgnoreCase(v)) anyHuman = true;
            }
        }
        return anyHuman ? Verdict.REVIEW : Verdict.APPROVE;
    }

    static Verdict guardChunkedAggregateByMemory(
            Verdict v,
            Map<String, Object> mem,
            ModerationConfidenceFallbackConfigEntity fb,
            Map<String, Object> policyConfig,
            String reviewStage,
            List<String> labels
    ) {
        if (v == null) return Verdict.REVIEW;
        if (v != Verdict.APPROVE) return v;
        if (mem == null || mem.isEmpty()) return v;
        Thresholds th = resolveThresholdsPreferred(policyConfig, reviewStage, labels, fb);
        double humanThreshold = th.tAllow;
        double maxScore = clamp01(asDoubleOrDefault(mem.get("maxScore"), 0.0), 0.0);
        if (maxScore >= humanThreshold) return Verdict.REVIEW;
        return v;
    }

    static Thresholds resolveThresholdsPreferred(
            Map<String, Object> policyConfig,
            String reviewStage,
            List<String> labels,
            ModerationConfidenceFallbackConfigEntity fb
    ) {
        if (policyConfig != null && !policyConfig.isEmpty()) {
            try {
                return resolveThresholdsRequired(policyConfig, reviewStage, labels);
            } catch (Exception ignore) {
            }
        }

        Double rr = fb == null ? null : fb.getChunkLlmRejectThreshold();
        if (rr == null) rr = fb == null ? null : fb.getLlmRejectThreshold();
        double tReject = clamp01(rr == null ? 0.75 : rr, 0.75);

        Double hh = fb == null ? null : fb.getChunkLlmHumanThreshold();
        if (hh == null) hh = fb == null ? null : fb.getLlmHumanThreshold();
        double tAllow = clamp01(hh == null ? 0.5 : hh, 0.5);

        if (tAllow > tReject) tAllow = tReject;
        return new Thresholds(tAllow, tReject, "fallback.chunk_config");
    }

    static Map<String, Object> buildFinalReviewAuditDetails(Map<String, Object> chunkProgressFinal, LlmModerationTestResponse finalReviewRes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scope", "finalReview");
        m.put("decisionSource", "finalReview");
        if (chunkProgressFinal != null) m.put("chunkProgressFinal", chunkProgressFinal);
        if (finalReviewRes != null) {
            if (finalReviewRes.getDecision() != null) m.put("finalReviewDecision", finalReviewRes.getDecision());
            if (finalReviewRes.getScore() != null) m.put("finalReviewScore", finalReviewRes.getScore());
            if (finalReviewRes.getModel() != null) m.put("finalReviewModel", finalReviewRes.getModel());
        }
        return m;
    }

    @SuppressWarnings("unused")
    static String buildChunkPromptText(
            com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity q,
            com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService.ChunkToProcess c,
            String raw,
            com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO cfg,
            Map<String, Object> mem
    ) {
        return buildChunkPromptText(q, c, raw, cfg, mem, List.of());
    }

    static String buildChunkPromptText(
            com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity q,
            com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService.ChunkToProcess c,
            String raw,
            com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO cfg,
            Map<String, Object> mem,
            List<ChunkImageRef> imageRefs
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("[CHUNK_REVIEW]\\n");
        sb.append("queueId: ").append(q == null ? "" : String.valueOf(q.getId())).append('\n');
        sb.append("contentType: ").append(q == null || q.getContentType() == null ? "" : q.getContentType().name()).append('\n');
        sb.append("contentId: ").append(q == null ? "" : String.valueOf(q.getContentId())).append('\n');
        sb.append("source: ").append(c.sourceType() == null ? "" : c.sourceType().name()).append('\n');
        if (c.fileAssetId() != null) sb.append("fileAssetId: ").append(c.fileAssetId()).append('\n');
        if (c.fileName() != null && !c.fileName().isBlank())
            sb.append("fileName: ").append(c.fileName().trim()).append('\n');
        int chunkIndex = c.chunkIndex() == null ? 0 : c.chunkIndex();
        sb.append("chunkIndex: ").append(chunkIndex).append('\n');
        sb.append("range: ").append(c.startOffset()).append('-').append(c.endOffset()).append('\n');
        String t = raw == null ? "" : raw.trim();

        if (cfg != null && Boolean.TRUE.equals(cfg.getEnableContextCompress())) {
            t = normalizeForPrompt(t);
        }
        List<String> hints = null;
        if (cfg != null && Boolean.TRUE.equals(cfg.getEnableTempIndexHints())) {
            hints = extractKeywords(t);
        }
        if (cfg != null && Boolean.TRUE.equals(cfg.getEnableGlobalMemory()) && mem != null && !mem.isEmpty()) {
            sb.append("\\n[GLOBAL_MEMORY]\\n");
            Object r = mem.get("riskTags");
            Object s = mem.get("maxScore");
            if (r != null) sb.append("riskTags: ").append(r).append('\n');
            if (s != null) sb.append("maxScore: ").append(s).append('\n');
            Object ents = mem.get("entities");
            if (ents != null) sb.append("entities: ").append(ents).append('\n');
            Object oq = mem.get("openQuestions");
            if (oq != null) sb.append("openQuestions: ").append(oq).append('\n');
            Object prev = null;
            try {
                Object sm = mem.get("summaries");
                if (chunkIndex > 0 && sm instanceof Map<?, ?> m) {
                    Object v = m.get(String.valueOf(chunkIndex - 1));
                    if (v == null) v = m.get(chunkIndex - 1);
                    if (v != null && !String.valueOf(v).isBlank()) prev = v;
                }
            } catch (Exception ignore) {
            }
            if (prev == null) prev = mem.get("prevSummary");
            if (prev != null && !String.valueOf(prev).isBlank()) {
                sb.append("\\n[PREV_CHUNK_SUMMARY]\\n");
                sb.append(String.valueOf(prev).trim()).append('\n');
            }
        }
        if (hints != null && !hints.isEmpty()) {
            sb.append("\\n[HINTS]\\n");
            sb.append("keywords: ").append(String.join(", ", hints)).append('\n');
        }
        if (imageRefs != null && !imageRefs.isEmpty()) {
            sb.append("\\n[IMAGES]\\n");
            for (ChunkImageRef r : imageRefs) {
                if (r == null || r.url == null || r.url.isBlank()) continue;
                String ph = r.placeholder == null || r.placeholder.isBlank() ? (r.index == null ? "" : "[[IMAGE_" + r.index + "]]") : r.placeholder.trim();
                sb.append(ph).append(": ").append(r.url.trim()).append('\n');
            }
        }
        sb.append("\\n[TEXT]\\n");
        if (t.length() > 5500) t = t.substring(0, 5500);
        sb.append(t);
        return sb.toString();
    }

    static long asLongOrDefault(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    static long clampLong(long v, long min, long max) {
        if (v < min) return min;
        return Math.min(v, max);
    }

    static Map<String, Object> buildTokenDiagnostics(
            String promptText,
            List<LlmModerationTestRequest.ImageInput> images,
            LlmModerationTestResponse res,
            com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt,
            int imagesCandidate,
            List<Integer> evidenceSourceChunks,
            List<String> evidencePlaceholdersUsed
    ) {
        LinkedHashMap<String, Object> diag = new LinkedHashMap<>();
        int promptChars = promptText == null ? 0 : promptText.length();
        int imageCount = images == null ? 0 : images.size();
        int maxImagesPerRequest = visionPrompt == null || visionPrompt.getVisionMaxImagesPerRequest() == null
                ? 10
                : clampInt(visionPrompt.getVisionMaxImagesPerRequest());
        Integer imageTokenBudget = visionPrompt == null ? null : visionPrompt.getVisionImageTokenBudget();
        Boolean highResolutionImages = visionPrompt == null ? null : visionPrompt.getVisionHighResolutionImages();
        Integer maxPixels = visionPrompt == null ? null : visionPrompt.getVisionMaxPixels();
        Integer tokensIn = (res == null || res.getUsage() == null) ? null : res.getUsage().getPromptTokens();

        int localUploads = 0;
        int dataUrls = 0;
        int remoteUrls = 0;
        int otherUrls = 0;
        if (images != null) {
            for (LlmModerationTestRequest.ImageInput in : images) {
                String kind = classifyImageUrlKind(in == null ? null : in.getUrl());
                switch (kind) {
                    case "local_upload" -> localUploads += 1;
                    case "data_url" -> dataUrls += 1;
                    case "remote_url" -> remoteUrls += 1;
                    default -> otherUrls += 1;
                }
            }
        }

        int estimatedBatchesByCount = imageCount == 0 ? 0 : (imageCount + maxImagesPerRequest - 1) / maxImagesPerRequest;

        diag.put("promptChars", promptChars);
        diag.put("imagesSent", imageCount);
        diag.put("imagesCandidate", Math.max(0, imagesCandidate));
        diag.put("imagesSelectedByEvidence", imageCount);
        if (evidenceSourceChunks != null && !evidenceSourceChunks.isEmpty()) {
            diag.put("evidenceSourceChunks", evidenceSourceChunks);
        }
        if (evidencePlaceholdersUsed != null && !evidencePlaceholdersUsed.isEmpty()) {
            diag.put("evidencePlaceholdersUsed", evidencePlaceholdersUsed);
        }
        diag.put("visionMaxImagesPerRequest", maxImagesPerRequest);
        if (imageTokenBudget != null) diag.put("visionImageTokenBudget", imageTokenBudget);
        if (highResolutionImages != null) diag.put("visionHighResolutionImages", highResolutionImages);
        if (maxPixels != null) diag.put("visionMaxPixels", maxPixels);
        diag.put("estimatedImageBatchesByCount", estimatedBatchesByCount);
        diag.put("imageUrlKinds", Map.of(
                "localUpload", localUploads,
                "dataUrl", dataUrls,
                "remoteUrl", remoteUrls,
                "other", otherUrls
        ));
        if (tokensIn != null) {
            diag.put("promptTokens", tokensIn);
            if (promptChars > 0) diag.put("promptTokensPerPromptChar", round3(tokensIn / (double) promptChars));
            if (imageCount > 0) diag.put("promptTokensPerImage", round3(tokensIn / (double) imageCount));
        }

        List<String> hypotheses = new ArrayList<>();
        if (localUploads > 0 || dataUrls > 0) {
            hypotheses.add("Detected local/data URL images; prompt token usage may increase.");
        }
        if (estimatedBatchesByCount > 1) {
            hypotheses.add("Multiple image batches were generated; check token budget and batching parameters.");
        }
        if (imagesCandidate > imageCount) {
            hypotheses.add("Evidence-only image mode is enabled; only evidence-hit images are uploaded.");
        }
        if (tokensIn != null && tokensIn >= 70_000) {
            hypotheses.add("Chunk input tokens are high (>=70000); verify URL form and batching strategy.");
        }
        if (!hypotheses.isEmpty()) diag.put("hypotheses", hypotheses);
        return diag;
    }

    static String classifyImageUrlKind(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return "other";
        String url = rawUrl.trim().toLowerCase(Locale.ROOT);
        if (url.startsWith("data:")) return "data_url";
        if (url.startsWith("/uploads/") || url.startsWith("uploads/") || url.startsWith("./uploads/") || url.startsWith("../uploads/")) {
            return "local_upload";
        }
        if (url.startsWith("http://") || url.startsWith("https://")) return "remote_url";
        return "other";
    }

    static double round3(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.round(v * 1000.0d) / 1000.0d;
    }

    static int clampInt(int v) {
        return Math.toIntExact(clampLong(v, 1, 50));
    }

    static List<Map<String, Object>> extractEntitiesFromText(String text, int chunkIndex, int max) {
        if (text == null || text.isBlank()) return List.of();
        int limit = Math.clamp(max, 0, 200);
        if (limit == 0) return List.of();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<Map<String, Object>> out = new ArrayList<>();

        java.util.regex.Matcher mUrl = java.util.regex.Pattern.compile("(?i)\\bhttps?://[^\\s<>\"]{6,200}").matcher(text);
        while (mUrl.find() && out.size() < limit) {
            appendUniqueRiskContact(out, seen, limit, "URL", mUrl.group(), chunkIndex);
        }

        java.util.regex.Matcher mWww = java.util.regex.Pattern.compile("(?i)\\bwww\\.[^\\s<>\"]{6,200}").matcher(text);
        while (mWww.find() && out.size() < limit) {
            appendUniqueRiskContact(out, seen, limit, "URL", mWww.group(), chunkIndex);
        }

        java.util.regex.Matcher mPhone = java.util.regex.Pattern.compile("\\b1\\d{10}\\b").matcher(text);
        while (mPhone.find() && out.size() < limit) {
            appendUniqueRiskContact(out, seen, limit, "PHONE", mPhone.group(), chunkIndex);
        }

        java.util.regex.Matcher mWechat = java.util.regex.Pattern.compile("(?i)\\b(?:wx|wechat)[:闂?]?([a-zA-Z][-_a-zA-Z0-9]{5,19})\\b").matcher(text);
        while (mWechat.find() && out.size() < limit) {
            appendUniqueRiskContact(out, seen, limit, "WECHAT", mWechat.group(1), chunkIndex);
        }
        return out;
    }

    private static void appendUniqueRiskContact(List<Map<String, Object>> out,
                                                Set<String> seen,
                                                int limit,
                                                String type,
                                                String rawValue,
                                                Integer chunkIndex) {
        if (out.size() >= limit) return;
        if (rawValue == null) return;
        String value = rawValue.trim();
        if (value.isEmpty()) return;
        String key = type + "|" + value;
        if (!seen.add(key)) return;
        out.add(Map.of("type", type, "value", value, "chunkIndex", chunkIndex));
    }

    static String normalizeForPrompt(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return "";
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        t = t.replaceAll("\\n{3,}", "nn");
        return t.trim();
    }

    static Object sanitizeModerationResponseForQueueOutput(ObjectMapper objectMapper, LlmModerationTestResponse res) {
        if (objectMapper == null) return res;
        if (res == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode n = objectMapper.valueToTree(res);
            if (n != null && n.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode o = (com.fasterxml.jackson.databind.node.ObjectNode) n;
                o.remove("promptMessages");
                com.fasterxml.jackson.databind.JsonNode lt = o.get("labelTaxonomy");
                if (lt != null && lt.isObject()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) lt).remove("labelMap");
                }
            }
            return n;
        } catch (Exception ignore) {
            return res;
        }
    }

    static final class EvidenceImageSelection {
        final List<ChunkImageRef> selectedRefs;
        final List<Integer> sourceChunkIndexes;
        final List<String> placeholders;

        EvidenceImageSelection(List<ChunkImageRef> selectedRefs, List<Integer> sourceChunkIndexes, List<String> placeholders) {
            this.selectedRefs = selectedRefs == null ? List.of() : selectedRefs;
            this.sourceChunkIndexes = sourceChunkIndexes == null ? List.of() : sourceChunkIndexes;
            this.placeholders = placeholders == null ? List.of() : placeholders;
        }
    }

    static EvidenceImageSelection selectEvidenceDrivenChunkImages(
            Map<String, Object> mem,
            Integer chunkIndex,
            List<ChunkImageRef> candidateRefs
    ) {
        if (candidateRefs == null || candidateRefs.isEmpty()) {
            return new EvidenceImageSelection(List.of(), List.of(), List.of());
        }
        Object raw = mem == null ? null : mem.get("llmEvidenceByChunk");
        if (!(raw instanceof Map<?, ?> byChunk) || byChunk.isEmpty()) {
            return new EvidenceImageSelection(List.of(), List.of(), List.of());
        }

        List<Integer> sourceChunkIndexes = new ArrayList<>();
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        int current = chunkIndex == null ? Integer.MAX_VALUE : chunkIndex;

        List<Integer> keys = collectPreviousChunkIndexes(byChunk, current, Comparator.reverseOrder());

        for (Integer idx : keys) {
            if (idx == null) continue;
            Collection<?> col = readChunkCollection(byChunk, idx);
            if (col == null) continue;

            boolean chunkUsed = false;
            for (Object item : col) {
                if (item == null) continue;
                List<String> ps = extractImagePlaceholdersFromEvidence(String.valueOf(item));
                if (ps.isEmpty()) continue;
                chunkUsed = true;
                for (String p : ps) {
                    if (p == null || p.isBlank()) continue;
                    placeholders.add(p.trim());
                    if (placeholders.size() == 64) break;
                }
                if (placeholders.size() == 64) break;
            }
            if (chunkUsed) sourceChunkIndexes.add(idx);
            if (sourceChunkIndexes.size() == 12 || placeholders.size() == 64) break;
        }

        if (placeholders.isEmpty()) {
            return new EvidenceImageSelection(List.of(), List.of(), List.of());
        }

        LinkedHashSet<String> allowed = new LinkedHashSet<>(placeholders);
        List<ChunkImageRef> selectedRefs = new ArrayList<>();
        for (ChunkImageRef ref : candidateRefs) {
            if (ref == null) continue;
            String p = ref.placeholder == null ? "" : ref.placeholder.trim();
            if (p.isEmpty()) continue;
            if (!allowed.contains(p)) continue;
            selectedRefs.add(ref);
        }
        List<String> picked = extractPlaceholdersFromRefs(selectedRefs);
        return new EvidenceImageSelection(selectedRefs, sourceChunkIndexes, picked);
    }

    static List<String> extractPlaceholdersFromRefs(List<ChunkImageRef> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (ChunkImageRef r : refs) {
            if (r == null || r.placeholder == null || r.placeholder.isBlank()) continue;
            out.add(r.placeholder.trim());
        }
        return out.isEmpty() ? List.of() : new ArrayList<>(out);
    }

    static List<ChunkImageRef> filterChunkImageRefsByIndices(List<ChunkImageRef> refs, Set<Integer> indices) {
        if (refs == null || refs.isEmpty() || indices == null || indices.isEmpty()) return List.of();
        ArrayList<ChunkImageRef> out = new ArrayList<>();
        for (ChunkImageRef ref : refs) {
            if (ref == null || ref.index == null || !indices.contains(ref.index)) continue;
            out.add(ref);
        }
        return out.isEmpty() ? List.of() : out;
    }

    static List<Integer> collectPreviousChunkIndexes(Map<?, ?> byChunk, int current, Comparator<Integer> comparator) {
        if (byChunk == null || byChunk.isEmpty()) return List.of();
        List<Integer> keys = new ArrayList<>();
        for (Object key : byChunk.keySet()) {
            Integer idx = toInt(key);
            if (idx == null || idx >= current) continue;
            keys.add(idx);
        }
        if (keys.isEmpty()) return List.of();
        keys.sort(comparator);
        return keys;
    }

    static Collection<?> readChunkCollection(Map<?, ?> byChunk, Integer idx) {
        if (byChunk == null || byChunk.isEmpty() || idx == null) return null;
        Object value = byChunk.get(String.valueOf(idx));
        if (value == null) value = byChunk.get(idx);
        return value instanceof Collection<?> col && !col.isEmpty() ? col : null;
    }

    static List<ChunkImageRef> mergeChunkImageRefs(List<ChunkImageRef> primaryRefs, List<ChunkImageRef> secondaryRefs) {
        if ((primaryRefs == null || primaryRefs.isEmpty()) && (secondaryRefs == null || secondaryRefs.isEmpty())) {
            return List.of();
        }
        ArrayList<ChunkImageRef> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ChunkImageRef ref : primaryRefs == null ? List.<ChunkImageRef>of() : primaryRefs) {
            appendChunkImageRef(out, seen, ref);
        }
        for (ChunkImageRef ref : secondaryRefs == null ? List.<ChunkImageRef>of() : secondaryRefs) {
            appendChunkImageRef(out, seen, ref);
        }
        return out.isEmpty() ? List.of() : out;
    }

    static void appendChunkImageRef(List<ChunkImageRef> out, Set<String> seen, ChunkImageRef ref) {
        if (ref == null) return;
        String placeholder = ref.placeholder == null ? "" : ref.placeholder.trim();
        String url = ref.url == null ? "" : ref.url.trim();
        String key = !placeholder.isEmpty() ? "ph|" + placeholder : (!url.isEmpty() ? "url|" + url : "");
        if (key.isEmpty() || !seen.add(key)) return;
        out.add(ref);
    }

    static List<String> summarizeEvidenceMemory(Map<String, Object> mem, Integer chunkIndex, int maxLines) {
        if (mem == null || mem.isEmpty()) return List.of();
        Object raw = mem.get("llmEvidenceByChunk");
        if (!(raw instanceof Map<?, ?> byChunk) || byChunk.isEmpty()) return List.of();

        int current = chunkIndex == null ? Integer.MAX_VALUE : chunkIndex;
        int limit = Math.clamp(maxLines, 1, 20);
        List<Integer> keys = collectPreviousChunkIndexes(byChunk, current, Comparator.reverseOrder());

        List<String> out = new ArrayList<>();
        for (Integer idx : keys) {
            if (idx == null) continue;
            Collection<?> col = readChunkCollection(byChunk, idx);
            if (col == null) continue;

            ArrayList<String> lines = new ArrayList<>();
            for (Object item : col) {
                if (item == null) continue;
                String text = String.valueOf(item).trim();
                if (text.isEmpty()) continue;
                lines.add(text);
                if (lines.size() >= 3) break;
            }
            if (lines.isEmpty()) continue;
            out.add("chunk-" + idx + ": " + String.join(" | ", lines));
            if (out.size() >= limit) break;
        }
        return out;
    }

    static List<String> collectChunkEvidenceForStepDetail(Map<String, Object> mem, int maxItems) {
        if (mem == null || mem.isEmpty()) return List.of();
        Object raw = mem.get("llmEvidenceByChunk");
        if (!(raw instanceof Map<?, ?> byChunk) || byChunk.isEmpty()) return List.of();

        List<Integer> keys = new ArrayList<>();
        for (Object k : byChunk.keySet()) {
            Integer idx = toInt(k);
            if (idx == null) continue;
            keys.add(idx);
        }
        if (keys.isEmpty()) return List.of();
        keys.sort(Comparator.naturalOrder());

        int limit = Math.clamp(maxItems, 1, 100);
        ArrayList<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Integer idx : keys) {
            if (idx == null) continue;
            Collection<?> col = readChunkCollection(byChunk, idx);
            if (col == null) continue;
            for (Object item : col) {
                if (item == null) continue;
                String text = String.valueOf(item).trim();
                if (text.isEmpty()) continue;
                String fp = fingerprintAggregateEvidenceItem(text);
                if (fp.isBlank()) fp = "raw|" + normalizeForAnchorMatch(text);
                if (!seen.add(fp)) continue;
                out.add(text);
                if (out.size() >= limit) return out;
            }
        }
        return out;
    }

    static String fingerprintAggregateEvidenceItem(String item) {
        if (item == null) return "";
        String t = item.trim();
        if (t.isEmpty()) return "";
        if (!(t.startsWith("{") && t.endsWith("}"))) return "raw|" + normalizeForAnchorMatch(t);
        try {
            Map<String, Object> node = EVIDENCE_MAPPER.readValue(t, STRING_OBJECT_MAP_TYPE);
            if (node == null) return "raw|" + normalizeForAnchorMatch(t);
            String text = canonicalEvidenceValue(String.valueOf(node.get("text") == null ? "" : node.get("text")));
            if (!text.isBlank()) return "text|" + text;
            String before = canonicalEvidenceValue(String.valueOf(node.get("before_context") == null ? "" : node.get("before_context")));
            String after = canonicalEvidenceValue(String.valueOf(node.get("after_context") == null ? "" : node.get("after_context")));
            if (!before.isBlank() || !after.isBlank()) return "ctx|" + before + "|" + after;
            return "raw|" + normalizeForAnchorMatch(t);
        } catch (Exception e) {
            return "raw|" + normalizeForAnchorMatch(t);
        }
    }

    @SuppressWarnings("unused")
    static List<LlmModerationTestRequest.ImageInput> selectChunkImageInputs(ObjectMapper objectMapper,
                                                                            String chunkText,
                                                                            Long fileAssetId,
                                                                            String extractedMetadataJson) {
        if (fileAssetId == null) return List.of();
        List<ChunkImageRef> refs = selectChunkImageRefs(objectMapper, chunkText, fileAssetId, extractedMetadataJson);
        return refsToImageInputs(refs, fileAssetId);
    }

    static final class ChunkImageRef {
        final Integer index;
        final String placeholder;
        final String url;
        final String mimeType;
        final Long fileAssetId;

        ChunkImageRef(Integer index, String placeholder, String url, String mimeType, Long fileAssetId) {
            this.index = index;
            this.placeholder = placeholder;
            this.url = url;
            this.mimeType = mimeType;
            this.fileAssetId = fileAssetId;
        }
    }

    static List<ChunkImageRef> selectChunkImageRefs(ObjectMapper objectMapper,
                                                    String chunkText,
                                                    Long fileAssetId,
                                                    String extractedMetadataJson) {
        if (objectMapper == null) return List.of();
        if (fileAssetId == null) return List.of();
        if (extractedMetadataJson == null || extractedMetadataJson.isBlank()) return List.of();
        Set<Integer> used = parseUsedImageIndices(chunkText);
        if (used.isEmpty()) return List.of();

        Map<String, Object> meta;
        try {
            meta = objectMapper.readValue(extractedMetadataJson, STRING_OBJECT_MAP_TYPE);
        } catch (Exception ignore) {
            return List.of();
        }
        Object listObj = meta.get("extractedImages");
        if (!(listObj instanceof List<?> list) || list.isEmpty()) return List.of();

        List<ChunkImageRef> picked = new ArrayList<>();
        LinkedHashSet<String> seenUrl = new LinkedHashSet<>();
        for (Object it : list) {
            if (!(it instanceof Map<?, ?> m)) continue;
            String placeholder = toStr(m.get("placeholder"));
            Integer idx = ModerationChunkImageSupport.resolveImageIndex(m.get("index"), placeholder, ModerationLlmAutoRunnerSupport::toInt);
            if (idx == null || !used.contains(idx)) continue;
            String url = toStr(m.get("url"));
            if (url == null || url.isBlank()) continue;
            String u = url.trim();
            if (!seenUrl.add(u)) continue;
            String ph = placeholder == null || placeholder.isBlank() ? "[[IMAGE_" + idx + "]]" : placeholder.trim();
            picked.add(new ChunkImageRef(idx, ph, u, toStr(m.get("mimeType")), fileAssetId));
        }
        if (picked.isEmpty()) return List.of();
        picked.sort(Comparator.comparingInt(a -> a.index == null ? 0 : a.index));
        return picked;
    }

    static List<LlmModerationTestRequest.ImageInput> refsToImageInputs(List<ChunkImageRef> refs, Long fileAssetId) {
        if (refs == null || refs.isEmpty()) return List.of();
        List<LlmModerationTestRequest.ImageInput> out = new ArrayList<>();
        for (ChunkImageRef r : refs) {
            if (r == null || r.url == null || r.url.isBlank()) continue;
            Long fid = r.fileAssetId != null ? r.fileAssetId : fileAssetId;
            if (fid == null) continue;
            LlmModerationTestRequest.ImageInput in = new LlmModerationTestRequest.ImageInput();
            in.setFileAssetId(fid);
            in.setUrl(r.url);
            in.setMimeType(r.mimeType);
            out.add(in);
        }
        return out;
    }

    static Set<Integer> parseUsedImageIndices(String text) {
        String t = text == null ? "" : text;
        java.util.regex.Matcher m = IMAGE_PLACEHOLDER.matcher(t);
        Set<Integer> out = new LinkedHashSet<>();
        while (m.find()) {
            Integer idx = toInt(m.group(1));
            if (idx != null) out.add(idx);
        }
        return out;
    }

    static List<String> filterChunkEvidence(List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (String s : evidence) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            out.add(t);
        }
        return out;
    }

    static String normalizeChunkImageEvidenceId(String evidenceItem, List<ChunkImageRef> actualChunkImageRefs) {
        if (evidenceItem == null) return "";
        String raw = evidenceItem.trim();
        if (!raw.startsWith("{") || !raw.endsWith("}")) return raw;
        if (actualChunkImageRefs == null || actualChunkImageRefs.isEmpty()) return raw;

        Map<String, Object> node;
        try {
            node = EVIDENCE_MAPPER.readValue(raw, STRING_OBJECT_MAP_TYPE);
        } catch (Exception e) {
            return raw;
        }
        if (node == null) return raw;

        String imageId = firstNonBlank(
                toStr(node.get("image_id")),
                toStr(node.get("imageId")),
                toStr(node.get("image"))
        );
        Integer ordinal = parseChunkEvidenceImageOrdinal(imageId);
        if (ordinal == null || ordinal <= 0 || ordinal > actualChunkImageRefs.size()) return raw;

        ChunkImageRef target = actualChunkImageRefs.get(ordinal - 1);
        if (target == null) return raw;
        String placeholder = firstNonBlank(
                target.placeholder,
                target.index == null ? null : "[[IMAGE_" + target.index + "]]"
        );
        if (placeholder == null || placeholder.isBlank()) return raw;

        node.put("image_id", placeholder);
        node.remove("imageId");
        node.remove("image");
        try {
            return EVIDENCE_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }

    static Integer parseChunkEvidenceImageOrdinal(String imageId) {
        if (imageId == null || imageId.isBlank()) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^img[\\s_-]*(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(imageId.trim());
        if (!matcher.matches()) return null;
        return toInt(matcher.group(1));
    }

    static String normalizeImageEvidenceToTextAnchor(String evidenceItem, String chunkText) {
        if (evidenceItem == null) return "";
        String raw = evidenceItem.trim();
        if (!raw.startsWith("{") || !raw.endsWith("}")) return raw;

        Map<String, Object> node;
        try {
            node = EVIDENCE_MAPPER.readValue(raw, STRING_OBJECT_MAP_TYPE);
        } catch (Exception e) {
            return raw;
        }
        if (node == null) return raw;

        String imageId = toStr(node.get("image_id"));
        if (imageId == null) imageId = toStr(node.get("imageId"));
        if (imageId == null) return raw;

        String snippet = firstNonBlank(
                toStr(node.get("quote")),
                toStr(node.get("text"))
        );
        if (snippet == null || snippet.isBlank()) return raw;
        if (!containsNormalizedText(chunkText, snippet)) return raw;

        AnchoredSnippet anchored = buildAnchoredSnippetFromChunk(chunkText, snippet);
        if (anchored == null || anchored.text == null || anchored.text.isBlank()) return raw;

        node.remove("image_id");
        node.remove("imageId");
        node.remove("image");
        node.put("text", clipTextForEvidenceJson(anchored.text, 240));
        if (anchored.beforeContext != null && !anchored.beforeContext.isBlank()) {
            node.put("before_context", anchored.beforeContext);
        }
        if (anchored.afterContext != null && !anchored.afterContext.isBlank()) {
            node.put("after_context", anchored.afterContext);
        }
        if (containsNormalizedText(anchored.text, toStr(node.get("quote")))) {
            node.remove("quote");
        }
        try {
            return EVIDENCE_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }

    static String extractByContextAnchors(Map<String, Object> node, String chunkText) {
        ModerationAnchorSnippetSupport.AnchorContext context = ModerationAnchorSnippetSupport.readAnchorContext(node);
        if (context == null) return null;

        if (chunkText == null || chunkText.isBlank()) return null;
        String r = extractBetweenAnchorsByRegex(chunkText, context.before(), context.after());
        return r == null || r.isBlank() ? null : r;
    }

    static String normalizeForAnchorMatch(String s) {
        if (s == null) return "";
        return s.replace('“', '"').replace('”', '"')
                .replace('‘', '\'').replace('’', '\'')
                .replaceAll("\\s+", " ")
                .replaceAll(" ?\" ?", "\"")
                .replaceAll(" ?' ?", "'");
    }

    static boolean containsNormalizedText(String text, String needle) {
        String t = text == null ? "" : text.trim();
        String n = needle == null ? "" : needle.trim();
        if (t.isEmpty() || n.isEmpty()) return false;
        if (t.contains(n)) return true;
        String nt = normalizeForAnchorMatch(t);
        String nn = normalizeForAnchorMatch(n);
        return nn.length() >= 6 && nt.contains(nn);
    }

    static boolean isSuspiciousEvidenceText(String text, String chunkText) {
        String t = text == null ? "" : text.trim();
        if (t.isBlank()) return true;
        if (t.length() > 320) return true;
        return !containsNormalizedText(chunkText, t);
    }

    static String clipTextForEvidenceJson(String text, int maxChars) {
        String t = text == null ? "" : text;
        int max = Math.max(20, maxChars);
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }

    static final class AnchoredSnippet {
        final String beforeContext;
        final String text;
        final String afterContext;

        AnchoredSnippet(String beforeContext, String text, String afterContext) {
            this.beforeContext = beforeContext;
            this.text = text;
            this.afterContext = afterContext;
        }
    }

    static AnchoredSnippet buildAnchoredSnippetFromChunk(String chunkText, String snippet) {
        String text = chunkText == null ? "" : chunkText;
        String needle = snippet == null ? "" : snippet.trim();
        if (text.isBlank() || needle.isBlank()) return null;
        int idx = text.indexOf(needle);
        if (idx < 0) return null;
        int end = idx + needle.length();
        int around = Math.clamp(15, 6, 40);
        String before = text.substring(Math.max(0, idx - around), idx).trim();
        String after = text.substring(end, Math.min(text.length(), end + around)).trim();
        String cleanedText = cleanExtractedSnippet(needle);
        String cleanedBefore = cleanExtractedSnippet(before);
        String cleanedAfter = cleanExtractedSnippet(after);
        if (cleanedText.isBlank()) return null;
        return new AnchoredSnippet(cleanedBefore, cleanedText, cleanedAfter);
    }

    static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            String t = toStr(value);
            if (t != null) return t;
        }
        return null;
    }

    static String fallbackViolationSnippet(String text, int violationStart) {
        return ModerationViolationSnippetSupport.fallbackViolationSnippet(
                text,
                violationStart,
                "\\n[",
                "\\n\\n",
                c -> c == '\n' || c == '\r' || c == '.' || c == ',' || c == ';' || c == '!' || c == '?',
                ModerationLlmAutoRunnerSupport::cleanExtractedSnippet
        );
    }

    static String cleanExtractedSnippet(String snippet) {
        if (snippet == null) return "";
        String cleaned = IMAGE_PLACEHOLDER.matcher(snippet).replaceAll(" ").trim();
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    static String extractBetweenAnchorsByRegex(String text, String before, String after) {
        return ModerationAnchorSnippetSupport.extractBetweenAnchorsByRegex(
                text,
                before,
                after,
                Math.clamp(500, 20, 2000),
                ModerationLlmAutoRunnerSupport::cleanExtractedSnippet,
                ModerationLlmAutoRunnerSupport::fallbackViolationSnippet
        );
    }

    static String canonicalEvidenceValue(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return "";
        s = IMAGE_PLACEHOLDER.matcher(s).replaceAll(" ").trim();
        return normalizeForAnchorMatch(s);
    }

    static List<String> buildEvidenceNormalizeReplay(List<String> before, List<String> after, int maxItems) {
        List<String> b = before == null ? List.of() : before;
        List<String> a = after == null ? List.of() : after;
        int max = Math.clamp(maxItems, 1, 10);
        int n = Math.max(b.size(), a.size());
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String bv = i < b.size() && b.get(i) != null ? b.get(i).trim() : "";
            String av = i < a.size() && a.get(i) != null ? a.get(i).trim() : "";
            if (bv.equals(av)) continue;
            out.add("idx=" + i + " | before=" + clipTextForEvidenceJson(bv, 240) + " | after=" + clipTextForEvidenceJson(av, 240));
            if (out.size() >= max) break;
        }
        return out.isEmpty() ? List.of() : out;
    }

    static List<String> filterChunkImageEvidence(List<String> evidence, List<ChunkImageRef> refs) {
        List<String> cleaned = filterChunkEvidence(evidence);
        if (cleaned.isEmpty()) return List.of();
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        if (refs != null) {
            for (ChunkImageRef r : refs) {
                if (r == null || r.placeholder == null || r.placeholder.isBlank()) continue;
                allowed.add(r.placeholder.trim());
            }
        }
        ArrayList<String> out = new ArrayList<>();
        for (String t : cleaned) {
            if (t == null) continue;
            String s = t.trim();
            if (s.isEmpty()) continue;
            List<String> ph = extractImagePlaceholdersFromEvidence(s);
            if (ph.isEmpty()) continue;
            ArrayList<String> picked = new ArrayList<>();
            for (String p : ph) {
                if (p == null || p.isBlank()) continue;
                if (allowed.isEmpty() || allowed.contains(p)) picked.add(p);
            }
            if (picked.isEmpty()) continue;
            out.add(String.join(" ", picked));
        }
        return out.isEmpty() ? List.of() : out;
    }

    static List<String> normalizeChunkEvidenceForLabels(ObjectMapper objectMapper,
                                                        List<String> evidence,
                                                        String chunkText,
                                                        List<ChunkImageRef> actualChunkImageRefs) {
        List<String> cleaned = filterChunkEvidence(evidence);
        if (cleaned.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String item : cleaned) {
            String t = item == null ? "" : item.trim();
            if (t.isEmpty()) continue;
            String normalized = normalizeChunkImageEvidenceId(t, actualChunkImageRefs);
            normalized = normalizeImageEvidenceToTextAnchor(normalized, chunkText);
            normalized = ensureAnchorEvidenceContainsText(objectMapper, normalized, chunkText);
            String key = evidenceFingerprint(objectMapper, normalized);
            if (key.isBlank()) key = "raw|" + normalized;
            if (!seen.add(key)) continue;
            out.add(normalized);
        }
        return out.isEmpty() ? List.of() : out;
    }

    static String ensureAnchorEvidenceContainsText(ObjectMapper objectMapper, String evidenceItem, String chunkText) {
        if (evidenceItem == null) return "";
        String raw = evidenceItem.trim();
        if (!raw.startsWith("{") || !raw.endsWith("}")) return raw;

        Map<String, Object> node;
        try {
            node = objectMapper.readValue(raw, STRING_OBJECT_MAP_TYPE);
        } catch (Exception e) {
            return raw;
        }
        if (node == null) return raw;

        Object textObj = node.get("text");
        String exists = textObj == null ? "" : String.valueOf(textObj).trim();
        if (!exists.isBlank() && !isSuspiciousEvidenceText(exists, chunkText)) return raw;

        String snippet = extractByContextAnchors(node, chunkText);
        if (snippet == null || snippet.isBlank()) return raw;

        snippet = IMAGE_PLACEHOLDER.matcher(snippet).replaceAll("").trim();
        if (snippet.isBlank()) return raw;

        snippet = cleanExtractedSnippet(snippet);
        if (snippet.isBlank()) return raw;

        node.put("text", clipTextForEvidenceJson(snippet, 240));
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }

    static String evidenceFingerprint(ObjectMapper objectMapper, String item) {
        if (item == null) return "";
        String t = item.trim();
        if (t.isEmpty()) return "";
        if (!(t.startsWith("{") && t.endsWith("}"))) return "raw|" + normalizeForAnchorMatch(t);
        try {
            Map<String, Object> node = objectMapper.readValue(t, STRING_OBJECT_MAP_TYPE);
            if (node == null) return "raw|" + normalizeForAnchorMatch(t);
            String text = canonicalEvidenceValue(node.get("text"));
            if (!text.isBlank()) return "text|" + text;
            String before = canonicalEvidenceValue(node.get("before_context"));
            String after = canonicalEvidenceValue(node.get("after_context"));
            if (!before.isBlank() || !after.isBlank()) return "ctx|" + before + "|" + after;
            return "raw|" + normalizeForAnchorMatch(t);
        } catch (Exception e) {
            return "raw|" + normalizeForAnchorMatch(t);
        }
    }

    static List<String> extractImagePlaceholdersFromEvidence(String text) {
        if (text == null) return List.of();
        java.util.regex.Matcher m = IMAGE_PLACEHOLDER.matcher(text);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        while (m.find()) {
            String idx = m.group(1);
            if (idx == null) continue;
            String t = idx.trim();
            if (t.isEmpty()) continue;
            out.add("[[IMAGE_" + t + "]]");
        }
        return new ArrayList<>(out);
    }

    static Integer parseImageIndexFromPlaceholder(String placeholder) {
        return ModerationChunkImageSupport.parseImageIndexFromPlaceholder(placeholder);
    }

    @SuppressWarnings("IfCanBeSwitch")
    static Integer toInt(Object v) {
        return RagValueSupport.toInteger(v);
    }

    static String toStr(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    static List<String> extractKeywords(String text) {
        if (text == null) return List.of();
        String t = text.trim();
        if (t.isEmpty()) return List.of();
        HashMap<String, Integer> freq = new HashMap<>();
        String[] parts = t.split("[^\\p{L}\\p{N}]+");
        for (String p : parts) {
            if (p == null) continue;
            String s = p.trim();
            if (s.length() < 2) continue;
            if (s.length() > 24) s = s.substring(0, 24);
            freq.put(s, freq.getOrDefault(s, 0) + 1);
        }
        if (freq.isEmpty()) return List.of();
        List<Map.Entry<String, Integer>> list = new ArrayList<>(freq.entrySet());
        list.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            return Integer.compare(b.getKey().length(), a.getKey().length());
        });
        int take = Math.clamp(list.size(), 0, 12);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < take; i++) out.add(list.get(i).getKey());
        return out;
    }

    static Verdict verdictFromScore(double score, double rejectThreshold, double humanThreshold) {
        double s = clamp01(score, 0.0);
        double rejectT = clamp01Strict(rejectThreshold);
        double humanT = clamp01Strict(humanThreshold);
        if (humanT > rejectT) humanT = rejectT;
        if (s >= rejectT) return Verdict.REJECT;
        if (s >= humanT) return Verdict.REVIEW;
        return Verdict.APPROVE;
    }

    static Verdict verdictFromDecisionAndScore(String decision, Double score, double rejectThreshold, double humanThreshold) {
        if ("REJECT".equalsIgnoreCase(decision)) return Verdict.REJECT;
        if ("HUMAN".equalsIgnoreCase(decision) || "REVIEW".equalsIgnoreCase(decision)) return Verdict.REVIEW;
        if ("APPROVE".equalsIgnoreCase(decision)) {
            if (score == null) return Verdict.APPROVE;
            return stricterVerdict(Verdict.APPROVE, verdictFromScore(clamp01(score), rejectThreshold, humanThreshold));
        }
        if (score == null) return Verdict.REVIEW;
        return verdictFromScore(clamp01(score), rejectThreshold, humanThreshold);
    }

    static Verdict stricterVerdict(Verdict a, Verdict b) {
        if (a == Verdict.REJECT || b == Verdict.REJECT) return Verdict.REJECT;
        if (a == Verdict.REVIEW || b == Verdict.REVIEW) return Verdict.REVIEW;
        return Verdict.APPROVE;
    }

    static String normalizeDecisionOrNull(String decision) {
        if (decision == null || decision.isBlank()) return null;
        return ModerationFallbackDecisionService.normalizeDecision(decision);
    }

    static Map<String, Object> summarizeLlmStage(LlmModerationTestResponse.Stage s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) return m;
        putSummaryCommonFields(
                m,
                s.getDecisionSuggestion(),
                s.getDecision(),
                s.getRiskScore(),
                s.getScore(),
                s.getSeverity(),
                s.getUncertainty(),
                s.getLabels(),
                s.getRiskTags(),
                s.getReasons(),
                s.getEvidence(),
                s.getInputMode(),
                s.getModel(),
                s.getLatencyMs(),
                s.getRawModelOutput()
        );
        if (s.getDescription() != null) m.put("description", s.getDescription());
        return m;
    }

    static String buildUserFacingRejectReason(LlmModerationTestResponse res, String fallback) {
        String fb = fallback == null ? "" : fallback.trim();
        if (fb.isEmpty()) fb = "Content violates policy";
        if (res == null) return fb;

        List<String> parts = new ArrayList<>();
        if (res.getReasons() != null) {
            for (String r : res.getReasons()) {
                String t = normalizeOneLine(r);
                if (t.isEmpty()) continue;
                parts.add(t);
                if (parts.size() >= 3) break;
            }
        }
        if (parts.isEmpty() && res.getRiskTags() != null) {
            List<String> tags = new ArrayList<>();
            for (String tag : res.getRiskTags()) {
                String t = normalizeOneLine(tag);
                if (t.isEmpty()) continue;
                tags.add(t);
                if (tags.size() >= 3) break;
            }
            if (!tags.isEmpty()) {
                parts.add("Matched tags: " + String.join(", ", tags));
            }
        }
        if (parts.isEmpty()) return fb;

        String joined = String.join("; ", parts);
        String out = normalizeOneLine(joined);
        if (out.length() > 160) out = out.substring(0, 160);
        return out.isEmpty() ? fb : out;
    }

    static String normalizeOneLine(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    @SuppressWarnings("IfCanBeSwitch")
    static boolean asBooleanRequired(Object v, String key) {
        if (v == null) throw new IllegalStateException("missing threshold: " + key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")) return false;
        throw new IllegalStateException("invalid boolean threshold: " + key);
    }

    static double asDoubleRequired(Object v, String key) {
        return ModerationThresholdSupport.asDoubleRequired(v, key);
    }

    static long asLongRequired(Object v, String key) {
        if (v == null) throw new IllegalStateException("missing threshold: " + key);
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new IllegalStateException("invalid long threshold: " + key, e);
        }
    }

    static double clamp01Strict(double v) {
        if (!Double.isFinite(v)) throw new IllegalStateException("invalid double value");
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    @SuppressWarnings("IfCanBeSwitch")
    static boolean asBooleanOrDefault(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim().toLowerCase();
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")) return false;
        return def;
    }

    static double asDoubleOrDefault(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    static double clamp01(Double v) {
        if (v == null || !Double.isFinite(v)) return 0.0;
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    static double clamp01(double v, double def) {
        if (!Double.isFinite(v)) return def;
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    static Map<String, Object> summarizeLlmRes(LlmModerationTestResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (r == null) return m;
        putSummaryCommonFields(
                m,
                r.getDecisionSuggestion(),
                r.getDecision(),
                r.getRiskScore(),
                r.getScore(),
                r.getSeverity(),
                r.getUncertainty(),
                r.getLabels(),
                r.getRiskTags(),
                r.getReasons(),
                r.getEvidence(),
                r.getInputMode(),
                r.getModel(),
                r.getLatencyMs(),
                r.getRawModelOutput()
        );
        if (r.getUsage() != null) m.put("usage", r.getUsage());
        if (r.getStages() != null) {
            m.put("hasTextStage", r.getStages().getText() != null);
            m.put("hasImageStage", r.getStages().getImage() != null);
            m.put("hasJudgeStage", r.getStages().getJudge() != null);
            m.put("hasUpgradeStage", r.getStages().getUpgrade() != null);
        }
        return m;
    }

    private static void putSummaryCommonFields(Map<String, Object> target,
                                               String decisionSuggestion,
                                               String decision,
                                               Double riskScore,
                                               Double score,
                                               String severity,
                                               Double uncertainty,
                                               List<String> labels,
                                               List<String> riskTags,
                                               List<String> reasons,
                                               List<String> evidence,
                                               String inputMode,
                                               String model,
                                               Long latencyMs,
                                               String rawModelOutput) {
        if (decisionSuggestion != null) target.put("decision_suggestion", decisionSuggestion);
        if (decision != null) target.put("decision", decision);
        if (riskScore != null) target.put("risk_score", riskScore);
        if (score != null) target.put("score", score);
        if (severity != null) target.put("severity", severity);
        if (uncertainty != null) target.put("uncertainty", uncertainty);
        if (labels != null && !labels.isEmpty()) target.put("labels", labels);
        if (riskTags != null && !riskTags.isEmpty()) target.put("riskTags", riskTags);
        putSummaryList(target, "reasons", reasons);
        putSummaryList(target, "evidence", evidence);
        if (inputMode != null) target.put("inputMode", inputMode);
        if (model != null) target.put("model", model);
        if (latencyMs != null) target.put("latencyMs", latencyMs);
        if (rawModelOutput != null) {
            String raw = rawModelOutput.length() > 1000 ? rawModelOutput.substring(0, 1000) : rawModelOutput;
            target.put("rawModelOutput", raw);
        }
    }

    private static void putSummaryList(Map<String, Object> target, String key, List<String> values) {
        if (values == null || values.isEmpty()) return;
        int take = Math.min(10, values.size());
        target.put(key, values.subList(0, take));
    }

    static String buildFinalReviewInput(Map<String, Object> mem) {
        if (mem == null || mem.isEmpty()) return "[EMPTY_MEMORY]";
        StringBuilder sb = new StringBuilder();
        Object desc = mem.get("imageDescription");
        if (desc != null && !desc.toString().isBlank()) {
            sb.append("[IMAGE_DESCRIPTION]\\n");
            String t = desc.toString().trim();
            if (t.length() > 1500) t = t.substring(0, 1500);
            sb.append(t).append("\\n\\n");
        }
        sb.append("[EVIDENCE_BOOK]\\n");
        Object risk = mem.get("riskTags");
        if (risk != null) sb.append("riskTags: ").append(risk).append('\n');
        Object ms = mem.get("maxScore");
        if (ms != null) sb.append("maxScore: ").append(ms).append('\n');
        Object ents = mem.get("entities");
        if (ents != null) sb.append("entities: ").append(ents).append('\n');
        Object ev = mem.get("evidence");
        if (ev != null) {
            sb.append("evidence: ").append(ev).append('\n');
        } else {
            List<String> fromChunk = collectChunkEvidenceForStepDetail(mem, 20);
            if (!fromChunk.isEmpty()) {
                sb.append("evidence: ").append(fromChunk).append('\n');
            }
        }
        Object oq = mem.get("openQuestions");
        if (oq != null) sb.append("openQuestions: ").append(oq).append('\n');
        String out = sb.toString();
        if (out.length() > 4000) out = out.substring(0, 4000);
        return out;
    }

}
