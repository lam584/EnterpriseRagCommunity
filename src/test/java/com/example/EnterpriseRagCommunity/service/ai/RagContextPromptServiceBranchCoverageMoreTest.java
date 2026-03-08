package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceBranchCoverageMoreTest {

    @Test
    void assemble_cfgNull_should_apply_defaults_and_render_default_header() {
        RagContextPromptService svc = new RagContextPromptService();

        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(1L);
        h.setChunkIndex(2);
        h.setScore(null);
        h.setTitle("T1");
        h.setContentText(asciiTokens(10));

        RagContextPromptService.AssembleResult r = svc.assemble("q", List.of(h), null, null);
        assertNotNull(r);
        assertEquals(ContextWindowPolicy.TOPK, r.getPolicy());
        assertEquals(10_000, r.getBudgetTokens());
        assertEquals(10, r.getUsedTokens());
        assertEquals(1, r.getSelected().size());

        String prompt = r.getContextPrompt();
        assertTrue(prompt.contains("[1]"));
        assertTrue(prompt.contains("post_id=1"));
        assertTrue(prompt.contains("chunk=2"));
        assertTrue(prompt.contains("标题：T1"));
        assertTrue(!prompt.contains("score="));
    }

    @Test
    void assemble_disabled_should_keep_nonNull_policy() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setEnabled(false);
        cfg.setPolicy(ContextWindowPolicy.FIXED);

        RagContextPromptService.AssembleResult r = svc.assemble("q", List.of(hit(1L, 0, 1.0, "t", "x")), cfg, null);
        assertNotNull(r);
        assertEquals(ContextWindowPolicy.FIXED, r.getPolicy());
        assertEquals(0, r.getBudgetTokens());
        assertEquals("", r.getContextPrompt());
    }

    @Test
    void assemble_sectionTitle_should_prefix_prompt() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 1, 200, 0, 10_000);
        cfg.setSectionTitle(" Sec ");
        cfg.setShowPostId(false);
        cfg.setShowChunkIndex(false);
        cfg.setShowScore(false);
        cfg.setShowTitle(false);

        RagContextPromptService.AssembleResult r = svc.assemble("q", List.of(hit(1L, 0, 1.0, "t", asciiTokens(5))), cfg, null);
        assertNotNull(r);
        assertTrue(r.getContextPrompt().startsWith("Sec"));
    }

    @Test
    void assemble_topk_hitsNull_should_append_extraInstruction_when_prompt_is_blank() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 1, 200, 0, 10_000);
        cfg.setExtraInstruction(" EXTRA ");

        RagContextPromptService.AssembleResult r = svc.assemble("q", null, cfg, null);
        assertNotNull(r);
        assertEquals("EXTRA", r.getContextPrompt());
    }

    @Test
    void assemble_topk_hitsNull_should_append_citationInstruction_when_prompt_is_blank() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 1, 200, 0, 10_000);
        cfg.setExtraInstruction("");

        CitationConfigDTO cite = new CitationConfigDTO();
        cite.setEnabled(true);
        cite.setCitationMode("MODEL_INLINE");
        cite.setInstructionTemplate(" CITE ");

        RagContextPromptService.AssembleResult r = svc.assemble("q", null, cfg, cite);
        assertNotNull(r);
        assertEquals("CITE", r.getContextPrompt());
    }

    @Test
    void assemble_topk_hitsWithNullAndScoreNull_should_drop_by_minScore() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 10, 200, 0, 10_000);
        cfg.setMinScore(0.5);

        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(1L);
        h.setChunkIndex(0);
        h.setScore(null);
        h.setTitle("t");
        h.setContentText(asciiTokens(10));

        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        hits.add(null);
        hits.add(h);
        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(0, r.getSelected().size());
        assertEquals(1, r.getDropped().size());
        assertEquals("minScore", r.getDropped().get(0).getReason());
    }

    @Test
    void assemble_sliding_remainingLessThan50_should_drop_budgetExceeded_and_continue() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.SLIDING, 10, 80, 0, 10_000);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 10.0, "t1", asciiTokens(40)),
                hit(2L, 0, 9.0, "t2", asciiTokens(100)),
                hit(3L, 0, 8.0, "t3", asciiTokens(10))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());
        assertEquals(50, r.getUsedTokens());
        assertTrue(r.getDropped().stream().anyMatch(it -> "budgetExceeded".equals(it.getReason())));
    }

    @Test
    void assemble_sliding_scanStage_should_skipNullHit_and_dropByMinScore_whenScoreNull() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.SLIDING, 10, 200, 0, 10_000);
        cfg.setMinScore(0.5);

        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(1L);
        h.setChunkIndex(0);
        h.setScore(null);
        h.setTitle("t");
        h.setContentText(asciiTokens(10));

        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        hits.add(null);
        hits.add(h);

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(0, r.getSelected().size());
        assertEquals(1, r.getDropped().size());
        assertEquals("minScore", r.getDropped().get(0).getReason());
        assertEquals(2, r.getDropped().get(0).getRank());
    }

    @Test
    void assemble_sliding_hitsNull_should_append_extraInstruction_when_prompt_is_blank() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.SLIDING, 3, 200, 0, 10_000);
        cfg.setExtraInstruction(" EXTRA ");

        RagContextPromptService.AssembleResult r = svc.assemble("q", null, cfg, null);
        assertNotNull(r);
        assertEquals("EXTRA", r.getContextPrompt());
        assertEquals(0, r.getSelected().size());
    }

    @Test
    void assemble_importance_scanStage_should_drop_by_requireTitle_and_emptyContent() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 5, 1000, 0, 10_000);
        cfg.setRequireTitle(true);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 1.0, "   ", asciiTokens(10)),
                hit(2L, 0, 1.0, "t2", "   "),
                hit(3L, 0, 1.0, "t3", asciiTokens(10))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(1, r.getSelected().size());
        assertEquals(3L, r.getSelected().get(0).getPostId());

        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertTrue(reasons.contains("requireTitle"));
        assertTrue(reasons.contains("emptyContent"));
    }

    @Test
    void assemble_importance_should_dedupKeepBest_by_title_cover_replace_and_drop_new() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 10, 10_000, 0, 10_000);
        cfg.setDedupByTitle(true);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 1.0, " Hello ", asciiTokens(100)),
                hit(2L, 0, 1.0, "hello", asciiTokens(10)),
                hit(3L, 0, 0.5, "HELLO", asciiTokens(40)),
                hit(4L, 0, 1.0, "other", asciiTokens(10))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());

        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertEquals(2, reasons.stream().filter("dedupTitle"::equals).count());
    }

    @Test
    void assemble_importance_should_dedupKeepBest_by_contentHash_cover_replace_and_drop_new() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 10, 10_000, 0, 10_000);
        cfg.setDedupByContentHash(true);

        String same = asciiTokens(50);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 1.0, "t1", same),
                hit(2L, 0, 2.0, "t2", same),
                hit(3L, 0, 0.5, "t3", same),
                hit(4L, 0, 1.0, "t4", asciiTokens(10))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());

        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertEquals(2, reasons.stream().filter("dedupContent"::equals).count());
    }

    @Test
    void assemble_hybrid_head_budgetExceeded_should_drop_and_continue() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.HYBRID, 2, 50, 0, 10_000);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 10.0, "t1", asciiTokens(100)),
                hit(2L, 0, 9.0, "t2", asciiTokens(30))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(1, r.getSelected().size());
        assertEquals(30, r.getUsedTokens());

        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertTrue(reasons.contains("budgetExceeded"));
    }

    @Test
    void assemble_hybrid_should_earlyReturn_when_remainingBudget_le_0() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.HYBRID, 3, 100, 0, 10_000);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 10.0, "h1", asciiTokens(60)),
                hit(2L, 0, 9.0, "h2", asciiTokens(40)),
                hit(3L, 0, 8.0, "h3", asciiTokens(10))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());
        assertEquals(100, r.getUsedTokens());
        assertEquals(0, r.getDropped().size());
    }

    @Test
    void assemble_hybrid_should_earlyReturn_when_remainingSlots_le_0() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.HYBRID, 1, 100, 0, 10_000);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 10.0, "h1", asciiTokens(20)),
                hit(2L, 0, 9.0, "h2", asciiTokens(20))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(1, r.getSelected().size());
        assertEquals(20, r.getUsedTokens());
        assertEquals(0, r.getDropped().size());
    }

    @Test
    void assemble_hybrid_rest_should_cover_canTake_nonNull_and_dedupKeepBest_threeWays() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.HYBRID, 6, 1000, 0, 10_000);
        cfg.setDedupByPostId(true);
        cfg.setDedupByTitle(true);
        cfg.setDedupByContentHash(true);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 10.0, "Head A", asciiTokens(10)),
                hit(10L, 0, 9.0, "Head B", asciiTokens(10)),
                hit(1L, 1, 8.0, "dup postId with head", asciiTokens(10)),
                hit(2L, 0, 1.0, "p2-low", asciiTokens(80)),
                hit(2L, 1, 10.0, "p2-high", asciiTokens(20)),
                hit(3L, 0, 2.0, "Foo   Bar", asciiTokens(20)),
                hit(4L, 0, 3.0, " foo bar ", asciiTokens(20)),
                hit(5L, 0, 2.0, "c1", "SAME"),
                hit(6L, 0, 3.0, "c2", "SAME")
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);

        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertTrue(reasons.contains("dedupPostId"));
        assertTrue(reasons.contains("dedupTitle"));
        assertTrue(reasons.contains("dedupContent"));
    }

    @Test
    void assemble_importance_canDpFalse_should_cover_sort_path_maxSamePostItems_and_budgetExceeded() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.IMPORTANCE, 21, 100, 0, 10_000);
        cfg.setMaxSamePostItems(1);

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 100.0, "t1", asciiTokens(60)),
                hit(1L, 1, 10.0, "t1b", asciiTokens(10)),
                hit(2L, 0, 1.0, "t2", asciiTokens(50)),
                hit(3L, 0, 1.0, "t3", asciiTokens(40))
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(2, r.getSelected().size());
        assertEquals(100, r.getUsedTokens());
        List<String> reasons = r.getDropped().stream().map(RagContextPromptService.Item::getReason).toList();
        assertTrue(reasons.contains("maxSamePostItems"));
        assertTrue(reasons.contains("budgetExceeded"));
    }

    @Test
    void assemble_citation_sourcesSection_should_render_sourcesText_but_not_append_instruction() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 1, 200, 0, 10_000);
        cfg.setExtraInstruction("   ");
        cfg.setSeparator("");

        CitationConfigDTO cite = new CitationConfigDTO();
        cite.setEnabled(true);
        cite.setCitationMode("SOURCES_SECTION");
        cite.setInstructionTemplate("CITE");
        cite.setMaxSources(1);
        cite.setSourcesTitle("来源");
        cite.setPostUrlTemplate("https://p/{postId}");
        cite.setIncludeUrl(true);

        List<RagPostChatRetrievalService.Hit> hits = List.of(hit(10L, 2, 1.0, "T", "CONTENT"));

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, cite);
        assertNotNull(r);
        assertEquals("CONTENT", r.getContextPrompt());
        assertTrue(r.getSourcesText().startsWith("来源："));
        assertTrue(r.getSourcesText().contains("https://p/10"));
    }

    @Test
    void assemble_headerTemplate_can_render_blank_then_should_not_append_header() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 1, 200, 0, 10_000);
        cfg.setItemHeaderTemplate("{postId}{chunkIndex}{score}{title}");
        cfg.setShowPostId(false);
        cfg.setShowChunkIndex(false);
        cfg.setShowScore(false);
        cfg.setShowTitle(false);
        cfg.setSeparator("");

        List<RagPostChatRetrievalService.Hit> hits = List.of(hit(1L, 0, 1.0, "T", "CONTENT"));

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals("CONTENT", r.getContextPrompt());
    }

    @Test
    void assemble_should_break_when_sb_exceeds_maxPromptChars() {
        RagContextPromptService svc = new RagContextPromptService();

        ContextClipConfigDTO cfg = baseCfg(ContextWindowPolicy.TOPK, 2, 10_000, 0, 200_000);
        cfg.setMaxPromptChars(1000);
        cfg.setSeparator("");

        String longText = "a".repeat(1200);
        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1L, 0, 1.0, "t1", longText),
                hit(2L, 0, 1.0, "t2", longText)
        );

        RagContextPromptService.AssembleResult r = svc.assemble("q", hits, cfg, null);
        assertNotNull(r);
        assertEquals(1, r.getSelected().size());
    }

    @Test
    void reflection_selectGreedyByScore_and_isBetterGreedyCandidate_should_cover_unreachable_branches() throws Exception {
        Class<?> candCls = Class.forName("com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService$Candidate");
        Class<?> stCls = Class.forName("com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService$SelectionState");

        Object st = newInstance(stCls);
        setField(stCls, st, "maxSamePostItems", 0);
        setField(stCls, st, "dedupByPostId", false);
        setField(stCls, st, "dedupByTitle", false);
        setField(stCls, st, "dedupByContentHash", false);

        Object candNullItem = newInstance(candCls);
        setField(candCls, candNullItem, "item", null);

        Object candTokNull = newInstance(candCls);
        RagContextPromptService.Item itTokNull = new RagContextPromptService.Item();
        itTokNull.setRank(1);
        itTokNull.setPostId(1L);
        itTokNull.setScore(1.0);
        setField(candCls, candTokNull, "item", itTokNull);
        setField(candCls, candTokNull, "tokens", null);

        Object candA = newCandidate(candCls, 2, 2L, 100.0, 60, null);
        Object candBig = newCandidate(candCls, 3, 3L, 1000.0, 200, null);

        List<Object> pool = new ArrayList<>();
        pool.add(null);
        pool.add(candNullItem);
        pool.add(candTokNull);
        pool.add(candA);
        pool.add(candBig);

        Method core = RagContextPromptService.class.getDeclaredMethod(
                "selectGreedyByScore",
                List.class,
                stCls,
                int.class,
                int.class,
                List.class,
                boolean.class,
                double.class,
                double.class,
                double.class,
                String.class
        );
        core.setAccessible(true);

        List<RagContextPromptService.Item> dropped = new ArrayList<>();
        Object out = core.invoke(null, pool, st, 3, 120, dropped, true, 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertNotNull(out);

        Method better = RagContextPromptService.class.getDeclaredMethod("isBetterGreedyCandidate", candCls, candCls, String.class);
        better.setAccessible(true);

        Object eqImportanceHigherScore = newCandidate(candCls, 1, 10L, 2.0, 4, null);
        Object eqImportanceLowerScore = newCandidate(candCls, 2, 10L, 1.0, 1, null);
        assertEquals(true, better.invoke(null, eqImportanceHigherScore, eqImportanceLowerScore, "REL_IMP_RED"));

        Object tokZeroA = newCandidate(candCls, 1, 11L, 1.0, 0, null);
        Object tokZeroB = newCandidate(candCls, 2, 11L, 1.0, 0, null);
        assertEquals(true, better.invoke(null, tokZeroA, tokZeroB, "REL_IMP_RED"));
    }

    @Test
    void buildChunkIds_should_cover_selectedNull_itemNull_and_postIdNull() {
        RagContextPromptService.AssembleResult r = new RagContextPromptService.AssembleResult();
        r.setSelected(null);
        assertNotNull(RagContextPromptService.buildChunkIds(r));

        RagContextPromptService.Item itNullId = new RagContextPromptService.Item();
        itNullId.setPostId(null);
        RagContextPromptService.AssembleResult r2 = new RagContextPromptService.AssembleResult();
        r2.setSelected(Arrays.asList(null, itNullId));
        assertNotNull(RagContextPromptService.buildChunkIds(r2));
    }

    @Test
    void renderSourcesText_should_cover_disabled_modeNull_sourcesTitleNull_and_sourceNull() {
        assertEquals("", RagContextPromptService.renderSourcesText(null, List.of()));

        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(false);
        assertEquals("", RagContextPromptService.renderSourcesText(cfg, List.of()));

        cfg.setEnabled(true);
        cfg.setCitationMode(null);
        cfg.setSourcesTitle("来源");
        assertEquals("", RagContextPromptService.renderSourcesText(cfg, List.of(new RagContextPromptService.CitationSource())));

        cfg.setCitationMode("BOTH");
        cfg.setSourcesTitle(null);
        assertEquals("", RagContextPromptService.renderSourcesText(cfg, List.of(new RagContextPromptService.CitationSource())));

        cfg.setSourcesTitle("来源");
        cfg.setIncludeTitle(true);
        cfg.setIncludeUrl(true);
        cfg.setIncludeScore(true);
        cfg.setIncludePostId(true);
        cfg.setIncludeChunkIndex(true);

        RagContextPromptService.CitationSource s = new RagContextPromptService.CitationSource();
        String txt = RagContextPromptService.renderSourcesText(cfg, Arrays.asList(null, s));
        assertTrue(txt.startsWith("来源："));
        assertTrue(txt.contains("[]"));
        assertFalse(txt.contains("score="));
        assertFalse(txt.contains("post_id="));
        assertFalse(txt.contains("chunk="));
    }

    private static Object newCandidate(Class<?> candCls, int rank, Long postId, Double score, Integer tokens, String text) throws Exception {
        Object c = newInstance(candCls);
        RagContextPromptService.Item it = new RagContextPromptService.Item();
        it.setRank(rank);
        it.setPostId(postId);
        it.setScore(score);
        it.setTokens(tokens);
        setField(candCls, c, "item", it);
        setField(candCls, c, "tokens", tokens);
        setField(candCls, c, "text", text);
        double rel = score == null ? 0.0 : score;
        setField(candCls, c, "relScore", rel);
        setField(candCls, c, "impScore", tokens == null || tokens <= 0 ? 0.0 : 1.0 / Math.sqrt(tokens));
        setField(candCls, c, "redScore", 0.0);
        setField(candCls, c, "finalScore", rel);
        return c;
    }

    private static Object newInstance(Class<?> cls) throws Exception {
        Constructor<?> ct = cls.getDeclaredConstructor();
        ct.setAccessible(true);
        return ct.newInstance();
    }

    private static void setField(Class<?> cls, Object obj, String name, Object value) throws Exception {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private static RagPostChatRetrievalService.Hit hit(Long postId, Integer chunkIndex, Double score, String title, String contentText) {
        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(postId);
        h.setChunkIndex(chunkIndex);
        h.setScore(score);
        h.setTitle(title);
        h.setContentText(contentText);
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
