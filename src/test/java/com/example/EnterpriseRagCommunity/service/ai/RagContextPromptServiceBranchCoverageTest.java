package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceBranchCoverageTest {

    @Test
    void assemble_disabled_should_return_empty_result() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setEnabled(false);
        cfg.setPolicy(null);

        RagContextPromptService.AssembleResult r = svc.assemble("q", List.of(hit(1, 0, 1.0, "t", "x")), cfg, null);
        assertNotNull(r);
        assertEquals(ContextWindowPolicy.TOPK, r.getPolicy());
        assertEquals(0, r.getBudgetTokens());
        assertEquals(0, r.getUsedTokens());
        assertEquals("", r.getContextPrompt());
        assertEquals("", r.getSourcesText());
        assertEquals(List.of(), r.getSelected());
        assertEquals(List.of(), r.getDropped());
        assertEquals(List.of(), r.getSources());
        assertNotNull(r.getChunkIds());
        assertEquals(List.of(), ((List<?>) r.getChunkIds().get("ids")));
    }

    @Test
    void assemble_budgetTokens_should_follow_policy_rules() {
        RagContextPromptService svc = new RagContextPromptService();
        List<RagPostChatRetrievalService.Hit> hits = List.of(hit(1, 0, 1.0, "t1", asciiTokens(10)));

        ContextClipConfigDTO fixed = baseCfg(ContextWindowPolicy.FIXED, 1, 123, 999, 10_000);
        RagContextPromptService.AssembleResult r1 = svc.assemble("q", hits, fixed, null);
        assertEquals(123, r1.getBudgetTokens());

        ContextClipConfigDTO adaptive = baseCfg(ContextWindowPolicy.ADAPTIVE, 1, 100, 0, 10_000);
        RagContextPromptService.AssembleResult r2 = svc.assemble(asciiTokens(1000), hits, adaptive, null);
        assertEquals(0, r2.getBudgetTokens());

        ContextClipConfigDTO topk = baseCfg(ContextWindowPolicy.TOPK, 1, 100, 40, 10_000);
        RagContextPromptService.AssembleResult r3 = svc.assemble("q", hits, topk, null);
        assertEquals(60, r3.getBudgetTokens());
    }

    @Test
    void assemble_topk_should_drop_by_minScore_and_requireTitle_and_emptyContent() {
        RagContextPromptService svc = new RagContextPromptService();
        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        hits.add(hit(1, 0, 0.1, "t1", asciiTokens(20)));
        hits.add(hit(2, 0, 1.0, "", asciiTokens(20)));
        hits.add(hit(3, 0, 1.0, "t3", " "));

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 10, 1000, 0, 10_000);
        cfg.setMinScore(0.5);
        cfg.setRequireTitle(true);

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(0, r.getSelected().size());
        assertEquals(3, r.getDropped().size());
        assertEquals("minScore", r.getDropped().get(0).getReason());
        assertEquals("requireTitle", r.getDropped().get(1).getReason());
        assertEquals("emptyContent", r.getDropped().get(2).getReason());
    }

    @Test
    void assemble_topk_should_dedup_postId_title_contentHash() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 10, 1000, 0, 10_000);
        cfg.setDedupByPostId(true);
        cfg.setDedupByTitle(true);
        cfg.setDedupByContentHash(true);

        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        hits.add(hit(1, 0, 1.0, "Hello", asciiTokens(10)));
        hits.add(hit(1, 1, 1.0, "World", asciiTokens(10)));
        hits.add(hit(2, 0, 1.0, " hello ", asciiTokens(10)));
        hits.add(hit(3, 0, 1.0, "Other", "SAME"));
        hits.add(hit(4, 0, 1.0, "Other2", "SAME"));

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());

        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertTrue(reasons.contains("dedupPostId"));
        assertTrue(reasons.contains("dedupTitle"));
        assertTrue(reasons.contains("dedupContent"));
    }

    @Test
    void assemble_topk_should_enforce_maxSamePostItems_and_budgetExceeded() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 4, 100, 0, 10_000);
        cfg.setMaxSamePostItems(1);
        cfg.setDedupByPostId(false);

        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        hits.add(hit(1, 0, 1.0, "t1", asciiTokens(90)));
        hits.add(hit(1, 1, 1.0, "t1", asciiTokens(5)));
        hits.add(hit(2, 0, 1.0, "t2", asciiTokens(20)));

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(1, r.getSelected().size());
        assertEquals(90, r.getUsedTokens());

        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertTrue(reasons.contains("maxSamePostItems"));
        assertTrue(reasons.contains("budgetExceeded"));
    }

    @Test
    void assemble_importance_should_mark_notSelected_for_unpicked_candidates() {
        RagContextPromptService svc = new RagContextPromptService();
        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1, 0, 1.0, "t1", asciiTokens(80)),
                hit(2, 0, 1.1, "t2", asciiTokens(80)),
                hit(3, 0, 1.2, "t3", asciiTokens(80))
        );

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 1, 1000, 0, 10_000);
        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(1, r.getSelected().size());

        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertEquals(2, reasons.stream().filter("notSelected"::equals).count());
    }

    @Test
    void assemble_importance_should_dedupKeepBest_by_postId() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 2, 1000, 0, 10_000);
        cfg.setDedupByPostId(true);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1, 0, 2.0, "t1", asciiTokens(100)),
                hit(1, 1, 3.0, "t1", asciiTokens(400)),
                hit(2, 0, 1.0, "t2", asciiTokens(120))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertTrue(reasons.contains("dedupPostId"));
    }

    @Test
    void assemble_hybrid_should_clip_tail_when_allowSlidingFill_and_remaining_ge_50() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.HYBRID, 3, 200, 0, 10_000);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1, 0, 0.1, "t1", asciiTokens(70)),
                hit(2, 0, 0.1, "t2", asciiTokens(70)),
                hit(3, 0, 10.0, "t3", asciiTokens(120))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(3, r.getSelected().size());
        assertEquals(200, r.getUsedTokens());
        assertTrue(r.getSelected().get(2).getTokens() <= 60);
    }

    @Test
    void assemble_should_build_sources_sourcesText_and_append_instructions() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 2, 1000, 0, 10_000);
        cfg.setShowPostId(true);
        cfg.setShowChunkIndex(true);
        cfg.setShowScore(true);
        cfg.setShowTitle(true);
        cfg.setExtraInstruction("EXTRA");

        CitationConfigDTO cite = new CitationConfigDTO();
        cite.setEnabled(true);
        cite.setMaxSources(1);
        cite.setPostUrlTemplate("https://p/{postId}");
        cite.setCitationMode("BOTH");
        cite.setInstructionTemplate("CITE");
        cite.setSourcesTitle("来源");
        cite.setIncludeTitle(true);
        cite.setIncludeUrl(true);
        cite.setIncludeScore(true);
        cite.setIncludePostId(true);
        cite.setIncludeChunkIndex(true);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(10, 2, 1.23456, "Hello", asciiTokens(10)),
                hit(11, 3, 1.0, "World", asciiTokens(10))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, cite);
        assertNotNull(r);
        assertEquals(1, r.getSources().size());
        assertNotNull(r.getSourcesText());
        assertTrue(r.getSourcesText().startsWith("来源："));
        assertTrue(r.getSourcesText().contains("https://p/10"));

        String prompt = r.getContextPrompt();
        assertTrue(prompt.contains("EXTRA"));
        assertTrue(prompt.endsWith("CITE"));
        assertTrue(prompt.indexOf("EXTRA") < prompt.indexOf("CITE"));
    }

    @Test
    void assemble_should_render_header_template_and_stop_by_maxPromptChars() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 10, 1000, 0, 10_000);
        cfg.setItemHeaderTemplate("i={i}|post={postId}|chunk={chunkIndex}|score={score}|title={title}\n");
        cfg.setSeparator("\n");
        cfg.setShowPostId(true);
        cfg.setShowChunkIndex(true);
        cfg.setShowScore(true);
        cfg.setShowTitle(true);
        cfg.setMaxPromptChars(30);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1, 9, 1.5, "T1", asciiTokens(200)),
                hit(2, 8, 1.4, "T2", asciiTokens(200))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());
        assertTrue(r.getContextPrompt().contains("i=1|post=1|chunk=9|score=1.5000|title=T1"));
    }

    @Test
    void approxTokens_and_truncateByApproxTokens_should_handle_edges() {
        assertEquals(0, RagContextPromptService.approxTokens(null));
        assertEquals(0, RagContextPromptService.approxTokens(""));
        assertEquals(1, RagContextPromptService.approxTokens("a"));
        assertEquals(1, RagContextPromptService.approxTokens("aaaa"));
        assertEquals(2, RagContextPromptService.approxTokens("aaaaa"));
        assertEquals(1, RagContextPromptService.approxTokens("中"));
        assertEquals(2, RagContextPromptService.approxTokens("a中"));

        assertEquals("", RagContextPromptService.truncateByApproxTokens("abc", 0));
        assertEquals("abc", RagContextPromptService.truncateByApproxTokens("abc", 100));
        assertEquals("aaaa", RagContextPromptService.truncateByApproxTokens("aaaaa", 1));
    }

    @Test
    void renderSourcesText_should_respect_mode_and_title() {
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(true);
        cfg.setCitationMode("NONE");
        cfg.setSourcesTitle("来源");

        String none = RagContextPromptService.renderSourcesText(cfg, List.of(source(1)));
        assertEquals("", none);

        cfg.setCitationMode("SOURCES_SECTION");
        cfg.setSourcesTitle("");
        String noTitle = RagContextPromptService.renderSourcesText(cfg, List.of(source(1)));
        assertEquals("", noTitle);

        cfg.setSourcesTitle("来源");
        String ok = RagContextPromptService.renderSourcesText(cfg, List.of(source(1)));
        assertTrue(ok.startsWith("来源："));
    }

    @Test
    void buildChunkIds_should_be_stable_for_null_and_empty() {
        Map<String, Object> a = RagContextPromptService.buildChunkIds(null);
        assertNotNull(a);
        assertEquals(List.of(), a.get("ids"));

        RagContextPromptService.AssembleResult r = new RagContextPromptService.AssembleResult();
        r.setSelected(List.of());
        Map<String, Object> b = RagContextPromptService.buildChunkIds(r);
        assertNotNull(b);
        assertEquals(List.of(), b.get("ids"));
    }

    private static RagContextPromptService.CitationSource source(int idx) {
        RagContextPromptService.CitationSource s = new RagContextPromptService.CitationSource();
        s.setIndex(idx);
        s.setTitle("t");
        s.setUrl("u");
        s.setScore(1.0);
        s.setPostId(1L);
        s.setChunkIndex(0);
        return s;
    }

    private static RagPostChatRetrievalService.Hit hit(long postId, int chunkIndex, double score, String title, String content) {
        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(postId);
        h.setChunkIndex(chunkIndex);
        h.setScore(score);
        h.setTitle(title);
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

    private static String asciiTokens(int tokens) {
        return "a".repeat(Math.max(0, tokens * 4));
    }
}
