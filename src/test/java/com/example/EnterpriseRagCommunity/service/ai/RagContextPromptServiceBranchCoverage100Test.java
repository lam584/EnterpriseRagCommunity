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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextPromptServiceBranchCoverage100Test {

    @Test
    void assemble_should_cover_citation_and_topk_additional_branches() {
        RagContextPromptService svc = new RagContextPromptService();
        ContextClipConfigDTO cfg = baseCfg();
        cfg.setRequireTitle(true);
        cfg.setDedupByPostId(true);
        cfg.setDedupByTitle(true);
        cfg.setCrossSourceDedup(true);
        cfg.setMaxSamePostItems(1);
        cfg.setExtraInstruction("  ");

        List<RagPostChatRetrievalService.Hit> hits = List.of(
                hit(1, null, "ok", "doc:1"),
                hit(2, "t2", null, "doc:2"),
                hit(3, "t3", "!!!", "doc:3"),
                hit(4, "t4", "same text", "doc:4"),
                hit(5, "t5", "same text", "doc:5")
        );

        CitationConfigDTO c1 = new CitationConfigDTO();
        c1.setEnabled(false);
        RagContextPromptService.AssembleResult r1 = svc.assemble("q", hits, cfg, c1);
        assertNotNull(r1);

        CitationConfigDTO c2 = new CitationConfigDTO();
        c2.setEnabled(true);
        c2.setCitationMode(null);
        RagContextPromptService.AssembleResult r2 = svc.assemble("q", hits, cfg, c2);
        assertNotNull(r2);

        CitationConfigDTO c3 = new CitationConfigDTO();
        c3.setEnabled(true);
        c3.setCitationMode("BOTH");
        c3.setInstructionTemplate("   ");
        RagContextPromptService.AssembleResult r3 = svc.assemble("q", hits, cfg, c3);
        assertNotNull(r3);
        assertTrue(r3.getDropped().stream().anyMatch(x -> "requireTitle".equals(x.getReason())));
        assertTrue(r3.getDropped().stream().anyMatch(x -> "emptyContent".equals(x.getReason())));
    }

    @Test
    void selectGreedy_should_cover_early_returns_best_null_and_reason_skip() throws Exception {
        Object st = newSelectionState();
        Method m = selectGreedyMethod();

        @SuppressWarnings("unchecked")
        List<Object> rNull = (List<Object>) m.invoke(null, null, st, 1, 100, new ArrayList<>(), false, 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(rNull.isEmpty());

        @SuppressWarnings("unchecked")
        List<Object> rEmpty = (List<Object>) m.invoke(null, List.of(), st, 1, 100, new ArrayList<>(), false, 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(rEmpty.isEmpty());

        @SuppressWarnings("unchecked")
        List<Object> rMaxItemsZero = (List<Object>) m.invoke(null, List.of(newCandidate(1L, 10, 1.0, 1)), st, 0, 100, new ArrayList<>(), false, 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(rMaxItemsZero.isEmpty());

        @SuppressWarnings("unchecked")
        List<Object> rBudgetZero = (List<Object>) m.invoke(null, List.of(newCandidate(1L, 10, 1.0, 1)), st, 1, 0, new ArrayList<>(), false, 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(rBudgetZero.isEmpty());

        Object itemNull = newCandidate(2L, 10, 1.0, 2);
        setField(itemNull, "item", null);
        List<RagContextPromptService.Item> dropped1 = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> rBestNull = (List<Object>) m.invoke(null, List.of(itemNull), st, 1, 100, dropped1, false, 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(rBestNull.isEmpty());
        assertTrue(dropped1.isEmpty());

        Object c1 = newCandidate(10L, 10, 0.7, 1);
        Object c2 = newCandidate(11L, 10, 0.6, 2);
        RagContextPromptService.Item item2 = (RagContextPromptService.Item) getField(c2, "item");
        item2.setReason("preset");
        List<RagContextPromptService.Item> dropped2 = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> rReasonSkip = (List<Object>) m.invoke(null, List.of(c1, c2), st, 1, 100, dropped2, false, 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertEquals(1, rReasonSkip.size());
        assertTrue(dropped2.isEmpty());

        Object cTokNull = newCandidate(12L, null, 0.8, 1);
        List<RagContextPromptService.Item> dropped3 = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> rTokNull = (List<Object>) m.invoke(null, List.of(cTokNull), st, 1, 100, dropped3, false, 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertEquals(1, rTokNull.size());
    }

    @Test
    void selectSliding_selectHybrid_and_redundancy_should_cover_remaining_private_branches() throws Exception {
        Object st = newSelectionState();

        @SuppressWarnings("unchecked")
        List<Object> s0 = (List<Object>) selectSlidingMethod().invoke(
                null, null, st, 1, 100, new ArrayList<RagContextPromptService.Item>(), 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(s0.isEmpty());

        Object withNullItem = newCandidate(1L, 10, 1.0, 1);
        setField(withNullItem, "item", null);
        @SuppressWarnings("unchecked")
        List<Object> s1 = (List<Object>) selectSlidingMethod().invoke(
                null, List.of(withNullItem, newCandidate(2L, 10, 1.0, 2)), st, 0, 100, new ArrayList<RagContextPromptService.Item>(), 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(s1.isEmpty());

        @SuppressWarnings("unchecked")
        List<Object> h0 = (List<Object>) selectHybridMethod().invoke(
                null, null, st, 1, 100, new ArrayList<RagContextPromptService.Item>(), 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(h0.isEmpty());

        @SuppressWarnings("unchecked")
        List<Object> h1 = (List<Object>) selectHybridMethod().invoke(
                null, List.of(), st, 1, 100, new ArrayList<RagContextPromptService.Item>(), 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertTrue(h1.isEmpty());

        Object hTokNull = newCandidate(3L, null, 1.0, 1);
        @SuppressWarnings("unchecked")
        List<Object> h2 = (List<Object>) selectHybridMethod().invoke(
                null, List.of(hTokNull), st, 1, 100, new ArrayList<RagContextPromptService.Item>(), 1.0, 1.0, 1.0, "REL_IMP_RED");
        assertEquals(1, h2.size());

        Method redundancy = declared("redundancyWithSelected", candidateClass(), List.class);
        assertEquals(0.0, (Double) redundancy.invoke(null, null, List.of()));
        assertEquals(0.0, (Double) redundancy.invoke(null, newCandidate(10L, 10, 1.0, 1), null));
        assertEquals(0.0, (Double) redundancy.invoke(null, newCandidate(10L, 10, 1.0, 1), List.of()));
        Object badSelected = newCandidate(10L, 10, 1.0, 1);
        setField(badSelected, "item", null);
        assertEquals(0.0, (Double) redundancy.invoke(null, newCandidate(10L, 10, 1.0, 1), List.of(badSelected)));

        @SuppressWarnings("unchecked")
        Set<String> tokenSet = (Set<String>) declared("tokenizeSet", String.class).invoke(null, "a b c");
        assertEquals(3, tokenSet.size());
    }

    @Test
    void canTake_markTaken_and_isBetter_should_cover_null_and_tiebreak_paths() throws Exception {
        Method canTake = declared("canTake", candidateClass(), selectionStateClass());
        Method markTaken = declared("markTaken", candidateClass(), selectionStateClass());
        Method isBetter = declared("isBetterGreedyCandidate", candidateClass(), candidateClass(), String.class);

        Object st = newSelectionState();
        setBoolean(st, "crossSourceDedup", true);

        assertEquals("invalid", canTake.invoke(null, null, st));

        Object cItemNull = newCandidate(1L, 10, 1.0, 1);
        setField(cItemNull, "item", null);
        assertEquals("invalid", canTake.invoke(null, cItemNull, st));

        Object cNoSource = newCandidate(2L, 10, 1.0, 1);
        setField(cNoSource, "textKey", "tk");
        setField(cNoSource, "sourceKey", null);
        assertNull(canTake.invoke(null, cNoSource, st));

        markTaken.invoke(null, null, st);
        markTaken.invoke(null, cItemNull, st);
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) getField(st, "textKeySources");
        assertTrue(map.isEmpty());

        assertFalse((Boolean) isBetter.invoke(null, null, newCandidate(3L, 10, 1.0, 1), "REL_IMP_RED"));
        assertTrue((Boolean) isBetter.invoke(null, newCandidate(3L, 10, 1.0, 1), null, "REL_IMP_RED"));

        Object aNone = newCandidate(10L, 10, 1.0, 5);
        Object bNone = newCandidate(11L, 10, 1.0, 3);
        assertFalse((Boolean) isBetter.invoke(null, aNone, bNone, "NONE"));

        Object a = newCandidate(20L, 8, 0.5, 2);
        Object b = newCandidate(21L, 9, 0.5, 3);
        setField(a, "finalScore", 1.0);
        setField(b, "finalScore", 1.0);
        setField(a, "relScore", 0.2);
        setField(b, "relScore", 0.2);
        assertTrue((Boolean) isBetter.invoke(null, a, b, "REL_IMP_RED"));

        setField(a, "tokens", 9);
        setField(b, "tokens", 8);
        assertFalse((Boolean) isBetter.invoke(null, a, b, "REL_IMP_RED"));
    }

    @Test
    void render_and_sources_helpers_should_cover_remaining_branches() throws Exception {
        RagContextPromptService.Item item = new RagContextPromptService.Item();
        item.setPostId(null);
        item.setChunkIndex(null);
        item.setScore(null);
        item.setTitle("   ");

        String h1 = (String) declared(
                "renderHeader",
                String.class,
                int.class,
                RagContextPromptService.Item.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class
        ).invoke(null, null, 1, item, true, true, true, true);
        assertTrue(h1.startsWith("[1]"));

        String h2 = (String) declared(
                "renderHeader",
                String.class,
                int.class,
                RagContextPromptService.Item.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class
        ).invoke(null, "s={score}", 1, item, true, true, true, true);
        assertEquals("s=", h2);

        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(null);
        @SuppressWarnings("unchecked")
        List<RagContextPromptService.CitationSource> s0 = (List<RagContextPromptService.CitationSource>) declared("buildSources", CitationConfigDTO.class, List.class)
                .invoke(null, cfg, List.of(item));
        assertTrue(s0.isEmpty());

        cfg.setEnabled(true);
        cfg.setMaxSources(2);
        cfg.setPostUrlTemplate(null);
        @SuppressWarnings("unchecked")
        List<RagContextPromptService.CitationSource> s1 = (List<RagContextPromptService.CitationSource>) declared("buildSources", CitationConfigDTO.class, List.class)
                .invoke(null, cfg, null);
        assertTrue(s1.isEmpty());

        assertNull(declared("buildPostUrl", CitationConfigDTO.class, Long.class).invoke(null, cfg, 1L));

        CitationConfigDTO txtCfg = new CitationConfigDTO();
        txtCfg.setEnabled(true);
        txtCfg.setCitationMode("BOTH");
        txtCfg.setSourcesTitle("来源");
        txtCfg.setIncludeTitle(true);
        RagContextPromptService.CitationSource src = new RagContextPromptService.CitationSource();
        src.setIndex(1);
        src.setTitle("   ");
        String txt = RagContextPromptService.renderSourcesText(txtCfg, List.of(src));
        assertEquals("来源：\n[1]", txt);
    }

    @Test
    void score_and_similarity_helpers_should_cover_remaining_null_and_guard_branches() throws Exception {
        Method prepare = declared("prepareCandidateScores", List.class);
        prepare.invoke(null, new Object[]{null});
        prepare.invoke(null, List.of());

        Object onlyNull = newCandidate(1L, 10, 1.0, 1);
        setField(onlyNull, "item", null);
        prepare.invoke(null, List.of(onlyNull));

        Object withNulls = newCandidate(2L, null, null, 2);
        prepare.invoke(null, List.of(withNulls));
        assertNotNull(getField(withNulls, "relScore"));
        assertNotNull(getField(withNulls, "impScore"));

        Method apply = declared("applyCandidateScore", candidateClass(), double.class, double.class, double.class, String.class, double.class);
        apply.invoke(null, null, 1.0, 1.0, 1.0, "REL_IMP_RED", 0.2);
        Object noItem = newCandidate(3L, 10, 1.0, 3);
        setField(noItem, "item", null);
        apply.invoke(null, noItem, 1.0, 1.0, 1.0, "REL_IMP_RED", 0.2);

        Method sync = declared("syncCandidateScoreToItem", candidateClass());
        sync.invoke(null, new Object[]{null});
        sync.invoke(null, noItem);

        Method similarity = declared("similarity", candidateClass(), candidateClass());
        assertEquals(0.0, (Double) similarity.invoke(null, null, newCandidate(4L, 10, 1.0, 4)));
        assertEquals(0.0, (Double) similarity.invoke(null, newCandidate(4L, 10, 1.0, 4), null));

        Object a = newCandidate(5L, 10, 1.0, 5);
        Object b = newCandidate(6L, 10, 1.0, 6);
        setField(a, "contentHash", 7L);
        setField(b, "contentHash", 8L);
        setField(a, "titleKey", "t");
        setField(b, "titleKey", null);
        setField(a, "tokenSet", new HashSet<String>());
        setField(b, "tokenSet", new HashSet<String>());
        double sim = (Double) similarity.invoke(null, a, b);
        assertEquals(1.0, sim);
    }

    private static ContextClipConfigDTO baseCfg(
            ) {
        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setEnabled(true);
        cfg.setPolicy(ContextWindowPolicy.TOPK);
        cfg.setMaxItems(6);
        cfg.setMaxContextTokens(200);
        cfg.setReserveAnswerTokens(0);
        cfg.setPerItemMaxTokens(5000);
        cfg.setMaxPromptChars(1000);
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

    private static RagPostChatRetrievalService.Hit hit(
            Integer chunkIndex,
            String title,
            String content,
            String docId
    ) {
        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(null);
        h.setChunkIndex(chunkIndex);
        h.setScore(1.0);
        h.setTitle(title);
        h.setContentText(content);
        h.setDocId(docId);
        return h;
    }

    private static Method selectGreedyMethod() throws Exception {
        return declared(
                "selectGreedyByScore",
                List.class,
                selectionStateClass(),
                int.class,
                int.class,
                List.class,
                boolean.class,
                double.class,
                double.class,
                double.class,
                String.class
        );
    }

    private static Method selectSlidingMethod() throws Exception {
        return declared(
                "selectSliding",
                List.class,
                selectionStateClass(),
                int.class,
                int.class,
                List.class,
                double.class,
                double.class,
                double.class,
                String.class
        );
    }

    private static Method selectHybridMethod() throws Exception {
        return declared(
                "selectHybrid",
                List.class,
                selectionStateClass(),
                int.class,
                int.class,
                List.class,
                double.class,
                double.class,
                double.class,
                String.class
        );
    }

    private static Class<?> candidateClass() throws ClassNotFoundException {
        return Class.forName(RagContextPromptService.class.getName() + "$Candidate");
    }

    private static Class<?> selectionStateClass() throws ClassNotFoundException {
        return Class.forName(RagContextPromptService.class.getName() + "$SelectionState");
    }

    private static Method declared(String name, Class<?>... paramTypes) throws Exception {
        Method m = RagContextPromptService.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m;
    }

    private static Object newSelectionState() throws Exception {
        Constructor<?> ctor = selectionStateClass().getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Object newCandidate(Long postId, Integer tokens, Double score, Integer rank) throws Exception {
        Constructor<?> ctor = candidateClass().getDeclaredConstructor();
        ctor.setAccessible(true);
        Object c = ctor.newInstance();

        RagContextPromptService.Item item = new RagContextPromptService.Item();
        item.setPostId(postId);
        item.setScore(score);
        item.setRank(rank);
        item.setTitle("t");

        setField(c, "item", item);
        setField(c, "tokens", tokens);
        setField(c, "text", "a".repeat(80));
        setField(c, "relScore", score == null ? 0.0 : score);
        setField(c, "impScore", 0.5);
        setField(c, "finalScore", score == null ? 0.0 : score);
        setField(c, "tokenSet", new HashSet<>(Set.of("a", "b")));
        setField(c, "textKey", "tk");
        setField(c, "sourceKey", "SRC");
        return c;
    }

    private static void setBoolean(Object o, String name, boolean v) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.setBoolean(o, v);
    }

    private static Object getField(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void setField(Object o, String name, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }
}
