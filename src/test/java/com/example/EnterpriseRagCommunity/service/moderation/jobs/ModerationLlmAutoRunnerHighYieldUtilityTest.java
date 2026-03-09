package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationLlmAutoRunnerHighYieldUtilityTest {

    @SuppressWarnings("unchecked")
    @Test
    void extractEntitiesFromText_shouldCoverLimitAndDeduplicate() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("extractEntitiesFromText", String.class, int.class, int.class);
        m.setAccessible(true);

        List<Map<String, Object>> empty1 = (List<Map<String, Object>>) m.invoke(null, null, 1, 10);
        List<Map<String, Object>> empty2 = (List<Map<String, Object>>) m.invoke(null, "x", 1, 0);
        assertTrue(empty1.isEmpty());
        assertTrue(empty2.isEmpty());

        String text = "http://a.example.com www.bbbb.com 13812345678 wx:abcde1 wx:abcde1";
        List<Map<String, Object>> out = (List<Map<String, Object>>) m.invoke(null, text, 9, 10);
        assertFalse(out.isEmpty());
        assertTrue(out.stream().anyMatch(x -> "URL".equals(x.get("type"))));
        assertTrue(out.stream().anyMatch(x -> "PHONE".equals(x.get("type"))));
        assertTrue(out.stream().anyMatch(x -> "WECHAT".equals(x.get("type"))));
    }

    @Test
    void evaluatePolicyVerdict_shouldCoverEscalateRejectApproveAndUpgradeFailed() throws Exception {
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
                "thresholds", Map.of("default", Map.of("T_allow", 0.3, "T_reject", 0.7)),
                "escalate_rules", Map.of("require_evidence", true)
        );

        LlmModerationTestResponse escalateRes = new LlmModerationTestResponse();
        escalateRes.setDecisionSuggestion("ESCALATE");
        escalateRes.setScore(0.2);
        Object eval1 = m.invoke(null, policy, "default", escalateRes, Map.of(), false, false);
        Object verdict1 = eval1.getClass().getDeclaredMethod("verdict").invoke(eval1);
        assertEquals(Verdict.REVIEW, verdict1);

        LlmModerationTestResponse rejectNoEvidence = new LlmModerationTestResponse();
        rejectNoEvidence.setDecisionSuggestion("REJECT");
        rejectNoEvidence.setScore(0.9);
        rejectNoEvidence.setEvidence(List.of());
        Object eval2 = m.invoke(null, policy, "reported", rejectNoEvidence, Map.of(), false, false);
        Object verdict2 = eval2.getClass().getDeclaredMethod("verdict").invoke(eval2);
        assertEquals(Verdict.REVIEW, verdict2);

        LlmModerationTestResponse allowRes = new LlmModerationTestResponse();
        allowRes.setDecisionSuggestion("ALLOW");
        allowRes.setScore(0.1);
        allowRes.setEvidence(List.of("ok"));
        Object eval3 = m.invoke(null, policy, "default", allowRes, Map.of("abuse", 0.95), false, false);
        Object verdict3 = eval3.getClass().getDeclaredMethod("verdict").invoke(eval3);
        assertEquals(Verdict.APPROVE, verdict3);

        Object eval4 = m.invoke(null, policy, "default", allowRes, Map.of(), true, true);
        Object verdict4 = eval4.getClass().getDeclaredMethod("verdict").invoke(eval4);
        assertEquals(Verdict.REVIEW, verdict4);
    }

    @Test
    void buildChunkPromptText_shouldIncludeMemoryHintsAndImages() throws Exception {
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
        q.setId(101L);
        q.setContentType(ContentType.POST);
        q.setContentId(202L);
        ModerationChunkReviewService.ChunkToProcess chunk = new ModerationChunkReviewService.ChunkToProcess(
                1L, ChunkSourceType.FILE_TEXT, 88L, "a.pdf", 2, 10, 20
        );
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setEnableContextCompress(true);
        cfg.setEnableGlobalMemory(true);
        cfg.setEnableTempIndexHints(true);
        Map<String, Object> mem = Map.of(
                "riskTags", List.of("abuse"),
                "maxScore", 0.91,
                "summaries", Map.of("1", "上一段摘要")
        );
        List<ModerationLlmAutoRunner.ChunkImageRef> refs = List.of(
                new ModerationLlmAutoRunner.ChunkImageRef(1, "[[IMAGE_1]]", "http://img/1.png", "image/png", 100L)
        );

        String prompt = (String) m.invoke(null, q, chunk, "  文本A\n\n\n文本B  ", cfg, mem, refs);
        assertTrue(prompt.contains("[CHUNK_REVIEW]"));
        assertTrue(prompt.contains("[GLOBAL_MEMORY]"));
        assertTrue(prompt.contains("[HINTS]"));
        assertTrue(prompt.contains("[IMAGES]"));
        assertTrue(prompt.contains("[[IMAGE_1]]"));
        assertTrue(prompt.contains("[TEXT]"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildTokenDiagnostics_shouldCoverImageKindsAndHypotheses() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "buildTokenDiagnostics",
                String.class,
                List.class,
                LlmModerationTestResponse.class,
                PromptsEntity.class,
                int.class,
                List.class,
                List.class
        );
        m.setAccessible(true);

        LlmModerationTestRequest.ImageInput i1 = new LlmModerationTestRequest.ImageInput();
        i1.setUrl("/uploads/a.png");
        LlmModerationTestRequest.ImageInput i2 = new LlmModerationTestRequest.ImageInput();
        i2.setUrl("data:image/png;base64,xx");
        LlmModerationTestRequest.ImageInput i3 = new LlmModerationTestRequest.ImageInput();
        i3.setUrl("https://img.example.com/a.png");

        LlmModerationTestResponse res = new LlmModerationTestResponse();
        LlmModerationTestResponse.Usage usage = new LlmModerationTestResponse.Usage();
        usage.setPromptTokens(80000);
        res.setUsage(usage);
        PromptsEntity prompt = new PromptsEntity();
        prompt.setVisionMaxImagesPerRequest(2);
        prompt.setVisionImageTokenBudget(3000);
        prompt.setVisionHighResolutionImages(true);
        prompt.setVisionMaxPixels(2048);

        Map<String, Object> diag = (Map<String, Object>) m.invoke(
                null,
                "hello world",
                List.of(i1, i2, i3),
                res,
                prompt,
                6,
                List.of(4, 3),
                List.of("[[IMAGE_1]]")
        );
        assertNotNull(diag.get("imageUrlKinds"));
        assertNotNull(diag.get("hypotheses"));
        assertEquals(3, diag.get("imagesSent"));
    }
}
