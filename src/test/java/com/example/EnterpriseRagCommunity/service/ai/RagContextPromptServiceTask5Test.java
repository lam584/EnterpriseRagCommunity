package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceTask5Test {

    @Test
    void assemble_importance_none_should_select_by_rank_order() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 2, 400, 0, 10_000);
        cfg.setAblationMode("NONE");
        cfg.setAlpha(1.0);
        cfg.setBeta(1.0);
        cfg.setGamma(1.0);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 0.2, RetrievalHitType.POST, "rank one"),
                hit(2L, 0, 0.1, RetrievalHitType.POST, "rank two"),
                hit(3L, 0, 0.99, RetrievalHitType.POST, "rank three")
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertEquals(List.of(1, 2), r.getSelected().stream().map(RagContextPromptService.Item::getRank).toList());
    }

    @Test
    void assemble_importance_relOnly_should_prefer_higher_relevance() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 1, 400, 0, 10_000);
        cfg.setAblationMode("REL_ONLY");
        cfg.setAlpha(1.0);
        cfg.setBeta(1.0);
        cfg.setGamma(1.0);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 0.40, RetrievalHitType.POST, ascii(120)),
                hit(2L, 0, 0.85, RetrievalHitType.POST, ascii(120))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertEquals(List.of(2), r.getSelected().stream().map(RagContextPromptService.Item::getRank).toList());
    }

    @Test
    void assemble_importance_relImp_should_prefer_information_density_when_weight_high() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 1, 400, 0, 10_000);
        cfg.setAblationMode("REL_IMP");
        cfg.setAlpha(0.1);
        cfg.setBeta(10.0);
        cfg.setGamma(0.0);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 0.95, RetrievalHitType.POST, ascii(400)),
                hit(2L, 0, 0.70, RetrievalHitType.POST, ascii(40))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertEquals(List.of(2), r.getSelected().stream().map(RagContextPromptService.Item::getRank).toList());
    }

    @Test
    void assemble_importance_relImpRed_should_penalize_redundancy() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 2, 600, 0, 10_000);
        cfg.setAblationMode("REL_IMP_RED");
        cfg.setAlpha(1.0);
        cfg.setBeta(0.0);
        cfg.setGamma(2.0);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 1.00, RetrievalHitType.POST, "same body for redundancy"),
                hit(2L, 0, 0.95, RetrievalHitType.POST, "same body for redundancy"),
                hit(3L, 0, 0.90, RetrievalHitType.POST, "another unique passage")
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertEquals(List.of(1, 3), r.getSelected().stream().map(RagContextPromptService.Item::getRank).toList());
    }

    @Test
    void assemble_topk_should_skip_budget_exceeded_and_continue() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 3, 100, 0, 10_000);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 1.0, RetrievalHitType.POST, ascii(480)),
                hit(2L, 0, 0.9, RetrievalHitType.POST, ascii(200)),
                hit(3L, 0, 0.8, RetrievalHitType.POST, ascii(200))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertEquals(List.of(2, 3), r.getSelected().stream().map(RagContextPromptService.Item::getRank).toList());
        assertTrue(r.getDropped().stream().anyMatch(x -> x.getRank() == 1 && "budgetExceeded".equals(x.getReason())));
    }

    @Test
    void assemble_adaptive_should_compute_zero_budget_and_drop_hit() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.ADAPTIVE, 2, 150, 0, 10_000);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 0.9, RetrievalHitType.POST, ascii(120))
        );

        RagContextPromptService.AssembleResult r = svc.assemble(ascii(500), hits, cfg, null);
        assertEquals(0, r.getBudgetTokens());
        assertEquals(0, r.getSelected().size());
        assertTrue(r.getDropped().stream().anyMatch(x -> "budgetExceeded".equals(x.getReason())));
    }

    @Test
    void assemble_topk_should_cover_dedup_branches_in_sequence() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 5, 300, 0, 10_000);
        cfg.setDedupByPostId(true);
        cfg.setDedupByTitle(true);
        cfg.setDedupByContentHash(true);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 0.90, RetrievalHitType.POST, "content-a", "Alpha"),
                hit(1L, 1, 0.80, RetrievalHitType.POST, "content-b", "Beta"),
                hit(2L, 0, 0.70, RetrievalHitType.POST, "content-c", " Alpha "),
                hit(3L, 0, 0.60, RetrievalHitType.POST, "content-a", "Gamma"),
                hit(4L, 0, 0.50, RetrievalHitType.POST, "content-d", "Delta")
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        Set<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).collect(Collectors.toSet());
        assertTrue(reasons.contains("dedupPostId"));
        assertTrue(reasons.contains("dedupTitle"));
        assertTrue(reasons.contains("dedupContent"));
    }

    @Test
    void assemble_topk_should_drop_cross_source_duplicate() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 4, 300, 0, 10_000);
        cfg.setCrossSourceDedup(true);
        cfg.setDedupByPostId(false);
        cfg.setDedupByTitle(false);
        cfg.setDedupByContentHash(false);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 0.90, RetrievalHitType.POST, "cross source same"),
                hit(2L, 0, 0.80, RetrievalHitType.COMMENT_VEC, "cross source same"),
                hit(3L, 0, 0.70, RetrievalHitType.POST, "another text")
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertTrue(r.getDropped().stream().anyMatch(x -> x.getRank() == 2 && "crossSourceDedup".equals(x.getReason())));
    }

    private static RagPostChatRetrievalService.Hit hit(long postId, int chunkIndex, double score, RetrievalHitType type, String content) {
        return hit(postId, chunkIndex, score, type, content, "t" + postId);
    }

    private static RagPostChatRetrievalService.Hit hit(
            long postId,
            int chunkIndex,
            double score,
            RetrievalHitType type,
            String content,
            String title
    ) {
        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(postId);
        h.setChunkIndex(chunkIndex);
        h.setScore(score);
        h.setType(type);
        h.setTitle(title);
        h.setContentText(content);
        return h;
    }

    private static ContextClipConfigDTO baseCfg(
            ContextWindowPolicy policy,
            int maxItems,
            int contextTokenBudget,
            int reserveAnswerTokens,
            int perItemMaxTokens
    ) {
        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setEnabled(true);
        cfg.setPolicy(policy);
        cfg.setMaxItems(maxItems);
        cfg.setContextTokenBudget(contextTokenBudget);
        cfg.setMaxContextTokens(contextTokenBudget);
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
