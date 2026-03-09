package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationLlmAutoRunnerUtilityEdgeBranchTest {

    @SuppressWarnings("unchecked")
    @Test
    void summarizeMethods_shouldTruncateAndMarkStageFlags() throws Exception {
        Method summarizeStage = ModerationLlmAutoRunner.class.getDeclaredMethod("summarizeLlmStage", LlmModerationTestResponse.Stage.class);
        summarizeStage.setAccessible(true);
        Method summarizeRes = ModerationLlmAutoRunner.class.getDeclaredMethod("summarizeLlmRes", LlmModerationTestResponse.class);
        summarizeRes.setAccessible(true);

        LlmModerationTestResponse.Stage stage = new LlmModerationTestResponse.Stage();
        stage.setDecisionSuggestion("ALLOW");
        stage.setDecision("APPROVE");
        stage.setRiskScore(0.2);
        stage.setScore(0.2);
        stage.setSeverity("low");
        stage.setUncertainty(0.1);
        stage.setReasons(List.of("r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9", "r10", "r11"));
        stage.setEvidence(List.of("e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "e10", "e11"));
        stage.setInputMode("TEXT");
        stage.setModel("m1");
        stage.setLatencyMs(12L);
        stage.setDescription("desc");
        stage.setRawModelOutput("x".repeat(1205));

        Map<String, Object> stageSummary = (Map<String, Object>) summarizeStage.invoke(null, stage);
        assertEquals(10, ((List<?>) stageSummary.get("reasons")).size());
        assertEquals(10, ((List<?>) stageSummary.get("evidence")).size());
        assertEquals(1000, String.valueOf(stageSummary.get("rawModelOutput")).length());

        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setDecisionSuggestion("REJECT");
        res.setDecision("REJECT");
        res.setScore(0.9);
        res.setUsage(new LlmModerationTestResponse.Usage());
        res.getUsage().setPromptTokens(123);
        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();
        stages.setText(stage);
        res.setStages(stages);
        res.setRawModelOutput("y".repeat(1400));

        Map<String, Object> resSummary = (Map<String, Object>) summarizeRes.invoke(null, res);
        assertEquals(true, resSummary.get("hasTextStage"));
        assertEquals(false, resSummary.get("hasImageStage"));
        assertEquals(false, resSummary.get("hasJudgeStage"));
        assertEquals(false, resSummary.get("hasUpgradeStage"));
        assertNotNull(resSummary.get("usage"));
        assertEquals(1000, String.valueOf(resSummary.get("rawModelOutput")).length());
    }

    @Test
    void evaluatePolicyVerdict_shouldCoverTagThresholdAndGrayZoneReason() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "evaluatePolicyVerdict",
                Map.class,
                String.class,
                LlmModerationTestResponse.class,
                Map.class,
                boolean.class,
                boolean.class
        );
        m.setAccessible(true);

        Map<String, Object> policy = Map.of(
                "thresholds", Map.of(
                        "default", Map.of("T_allow", 0.2, "T_reject", 0.8),
                        "by_label", Map.of("abuse", Map.of("T_allow", 0.1, "T_reject", 0.75))
                ),
                "escalate_rules", Map.of("require_evidence", false)
        );

        LlmModerationTestResponse hitTag = new LlmModerationTestResponse();
        hitTag.setDecisionSuggestion("ALLOW");
        hitTag.setScore(0.74);
        hitTag.setRiskTags(List.of("abuse"));
        hitTag.setLabels(List.of("abuse"));
        hitTag.setEvidence(List.of("e"));
        Object evalTag = m.invoke(null, policy, "default", hitTag, Map.of("abuse", 0.7), false, false);
        Verdict tagVerdict = (Verdict) evalTag.getClass().getDeclaredMethod("verdict").invoke(evalTag);
        Map<?, ?> tagDetails = (Map<?, ?>) evalTag.getClass().getDeclaredMethod("details").invoke(evalTag);
        assertEquals(Verdict.REJECT, tagVerdict);
        assertEquals(true, tagDetails.get("tagThresholdHit"));
        assertEquals("policy.by_label", tagDetails.get("thresholdSource"));

        LlmModerationTestResponse gray = new LlmModerationTestResponse();
        gray.setDecisionSuggestion("ALLOW");
        gray.setScore(0.6);
        gray.setEvidence(List.of("ev"));
        Object evalGray = m.invoke(null, policy, "default", gray, Map.of(), false, false);
        Verdict grayVerdict = (Verdict) evalGray.getClass().getDeclaredMethod("verdict").invoke(evalGray);
        Map<?, ?> grayDetails = (Map<?, ?>) evalGray.getClass().getDeclaredMethod("details").invoke(evalGray);
        assertEquals(Verdict.REVIEW, grayVerdict);
        assertEquals("threshold_gray_zone", grayDetails.get("reason"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void collectChunkEvidenceForStepDetail_shouldDeduplicateAndRespectLimit() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("collectChunkEvidenceForStepDetail", Map.class, int.class);
        m.setAccessible(true);

        Map<Object, Object> byChunk = new LinkedHashMap<>();
        byChunk.put("1", List.of("{\"text\":\"Alpha\"}", "{\"text\":\"Alpha\"}", "Beta"));
        byChunk.put(2, List.of("Beta", "{\"before_context\":\"b1\",\"after_context\":\"b2\"}", "   "));
        byChunk.put("x", List.of("ignored"));
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("llmEvidenceByChunk", byChunk);

        List<String> out = (List<String>) m.invoke(null, mem, 3);
        assertEquals(3, out.size());
        assertTrue(out.stream().anyMatch(x -> x.contains("\"text\":\"Alpha\"") || x.equals("Alpha")));
        assertTrue(out.stream().anyMatch("Beta"::equals));
        assertTrue(out.stream().anyMatch(x -> x.contains("\"before_context\"")));
    }

    @Test
    void buildChunkPromptText_shouldUsePrevSummaryAndImageFallbackPlaceholder() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "buildChunkPromptText",
                ModerationQueueEntity.class,
                ModerationChunkReviewService.ChunkToProcess.class,
                String.class,
                ModerationChunkReviewConfigDTO.class,
                Map.class,
                List.class
        );
        m.setAccessible(true);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(9L);
        q.setContentType(ContentType.COMMENT);
        q.setContentId(99L);
        ModerationChunkReviewService.ChunkToProcess chunk = new ModerationChunkReviewService.ChunkToProcess(
                77L, ChunkSourceType.FILE_TEXT, null, "", 1, 3, 5
        );
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setEnableGlobalMemory(true);
        cfg.setEnableContextCompress(false);
        cfg.setEnableTempIndexHints(false);

        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("riskTags", List.of("r1"));
        mem.put("maxScore", 0.2);
        mem.put("prevSummary", "fallback summary");

        List<ModerationLlmAutoRunner.ChunkImageRef> refs = new ArrayList<>();
        refs.add(new ModerationLlmAutoRunner.ChunkImageRef(2, "", "https://img/2.png", "image/png", 1L));
        refs.add(new ModerationLlmAutoRunner.ChunkImageRef(3, null, "", "image/png", 1L));

        String prompt = (String) m.invoke(null, q, chunk, "text", cfg, mem, refs);
        assertTrue(prompt.contains("[PREV_CHUNK_SUMMARY]"));
        assertTrue(prompt.contains("fallback summary"));
        assertTrue(prompt.contains("[[IMAGE_2]]: https://img/2.png"));
        assertFalse(prompt.contains("[[IMAGE_3]]"));
    }
}
