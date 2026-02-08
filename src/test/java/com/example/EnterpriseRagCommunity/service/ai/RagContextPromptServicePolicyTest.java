package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServicePolicyTest {

    @Test
    void assemble_sliding_should_clip_last_item_to_remaining_budget() {
        RagContextPromptService svc = new RagContextPromptService();

        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        hits.add(hit(1, 0, 0.9, ascii(240)));
        hits.add(hit(2, 0, 0.8, ascii(400)));

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.SLIDING, 2, 110, 0, 10_000);

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(110, r.getBudgetTokens());
        assertEquals(110, r.getUsedTokens());
        assertEquals(2, r.getSelected().size());

        RagContextPromptService.Item last = r.getSelected().get(1);
        assertNotNull(last);
        assertTrue(last.getTokens() <= 50);
    }

    @Test
    void assemble_importance_should_prefer_high_value_dense_items_over_first_hit() {
        RagContextPromptService svc = new RagContextPromptService();

        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        hits.add(hit(1, 0, 3.0, ascii(720)));
        hits.add(hit(2, 0, 2.5, ascii(720)));
        hits.add(hit(3, 0, 1.8, ascii(120)));
        hits.add(hit(4, 0, 1.7, ascii(120)));
        hits.add(hit(5, 0, 1.6, ascii(120)));

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 2, 200, 0, 10_000);

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());
        assertEquals(3, r.getSelected().get(0).getRank());
        assertEquals(4, r.getSelected().get(1).getRank());
    }

    @Test
    void assemble_hybrid_should_keep_head_hits_then_fill_with_sliding_truncation() {
        RagContextPromptService svc = new RagContextPromptService();

        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        hits.add(hit(1, 0, 0.2, ascii(160)));
        hits.add(hit(2, 0, 0.2, ascii(160)));
        hits.add(hit(3, 0, 2.0, ascii(320)));

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.HYBRID, 3, 140, 0, 10_000);

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(3, r.getSelected().size());
        assertEquals(1, r.getSelected().get(0).getRank());
        assertEquals(2, r.getSelected().get(1).getRank());
        assertEquals(3, r.getSelected().get(2).getRank());
        assertEquals(140, r.getUsedTokens());
    }

    private static RagPostChatRetrievalService.Hit hit(long postId, int chunkIndex, double score, String content) {
        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(postId);
        h.setChunkIndex(chunkIndex);
        h.setScore(score);
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
        cfg.setReserveAnswerTokens(reserveAnswerTokens);
        cfg.setPerItemMaxTokens(perItemMaxTokens);
        cfg.setMaxPromptChars(1_000_000);
        cfg.setDedupByPostId(false);
        cfg.setDedupByTitle(false);
        cfg.setDedupByContentHash(false);
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

