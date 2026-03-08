package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceTask2Test {

    @Test
    void assemble_importance_relImpRed_should_penalize_redundancy_and_keep_diverse_context() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 2, 500, 0, 10_000);
        cfg.setAlpha(1.0);
        cfg.setBeta(1.0);
        cfg.setGamma(3.0);
        cfg.setAblationMode("REL_IMP_RED");
        cfg.setCrossSourceDedup(false);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1, 0, 0.99, RetrievalHitType.POST, "same text alpha beta gamma"),
                hit(2, 0, 0.98, RetrievalHitType.COMMENT_VEC, "same text alpha beta gamma"),
                hit(3, 0, 0.86, RetrievalHitType.POST, "unique text delta epsilon zeta")
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());
        assertEquals(1, r.getSelected().get(0).getRank());
        assertEquals(3, r.getSelected().get(1).getRank());
        assertTrue(r.getDropped().stream().anyMatch(x -> x.getRank() == 2 && "notSelected".equals(x.getReason())));
    }

    @Test
    void assemble_importance_should_apply_cross_source_dedup() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 3, 500, 0, 10_000);
        cfg.setCrossSourceDedup(true);
        cfg.setDedupByPostId(false);
        cfg.setDedupByTitle(false);
        cfg.setDedupByContentHash(false);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1, 0, 1.0, RetrievalHitType.POST, "cross source same body"),
                hit(2, 0, 0.95, RetrievalHitType.COMMENT_VEC, "cross source same body"),
                hit(3, 0, 0.90, RetrievalHitType.POST, "another body")
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());
        assertTrue(r.getDropped().stream().anyMatch(x -> "crossSourceDedup".equals(x.getReason())));
    }

    @Test
    void assemble_importance_budget_greedy_should_skip_oversized_and_take_next_candidates() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 3, 100, 0, 10_000);
        cfg.setAblationMode("REL_ONLY");
        cfg.setAlpha(1.0);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1, 0, 1.0, RetrievalHitType.POST, ascii(480)),
                hit(2, 0, 0.9, RetrievalHitType.POST, ascii(240)),
                hit(3, 0, 0.8, RetrievalHitType.POST, ascii(160))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());
        assertEquals(100, r.getUsedTokens());
        assertTrue(r.getDropped().stream().anyMatch(x -> x.getRank() == 1 && "budgetExceeded".equals(x.getReason())));
    }

    @Test
    void assemble_should_output_selected_reason_and_score_breakdown() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.SLIDING, 1, 200, 0, 10_000);
        cfg.setAblationMode("REL_IMP_RED");
        cfg.setAlpha(1.0);
        cfg.setBeta(1.0);
        cfg.setGamma(1.0);

        RagContextPromptService.AssembleResult r = svc.assemble(
                "q",
                List.of(hit(1, 0, 0.8, RetrievalHitType.POST, ascii(200))),
                cfg,
                null
        );
        assertNotNull(r);
        assertEquals(1, r.getSelected().size());
        RagContextPromptService.Item item = r.getSelected().get(0);
        assertEquals("selected", item.getReason());
        assertNotNull(item.getRelScore());
        assertNotNull(item.getImpScore());
        assertNotNull(item.getRedScore());
        assertNotNull(item.getFinalScore());
    }

    private static RagPostChatRetrievalService.Hit hit(long postId, int chunkIndex, double score, RetrievalHitType type, String content) {
        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(postId);
        h.setChunkIndex(chunkIndex);
        h.setScore(score);
        h.setType(type);
        h.setTitle("t" + postId);
        h.setContentText(content);
        return h;
    }

    private static ContextClipConfigDTO baseCfg(
            ContextWindowPolicy policy,
            int maxItems,
            int maxContextTokens,
            int reserveAnswerTokens,
            int perItemMaxTokens
    ) {
        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setEnabled(true);
        cfg.setPolicy(policy);
        cfg.setMaxItems(maxItems);
        cfg.setMaxContextTokens(maxContextTokens);
        cfg.setContextTokenBudget(maxContextTokens);
        cfg.setReserveAnswerTokens(reserveAnswerTokens);
        cfg.setPerItemMaxTokens(perItemMaxTokens);
        cfg.setMaxPromptChars(1_000_000);
        cfg.setDedupByPostId(false);
        cfg.setDedupByTitle(false);
        cfg.setDedupByContentHash(false);
        cfg.setCrossSourceDedup(false);
        cfg.setRequireTitle(false);
        cfg.setMaxSamePostItems(0);
        cfg.setSectionTitle("");
        cfg.setItemHeaderTemplate("");
        cfg.setSeparator("");
        cfg.setExtraInstruction("");
        cfg.setShowPostId(false);
        cfg.setShowChunkIndex(false);
        cfg.setShowScore(false);
        cfg.setShowTitle(false);
        return cfg;
    }

    private static String ascii(int n) {
        return "a".repeat(Math.max(0, n));
    }
}
