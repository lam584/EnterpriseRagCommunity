package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipTestResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextClipTestServiceTest {

    @Test
    void test_shouldUseDefaultFourModesAndReturnTokenDiffAgainstPrimary() {
        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setPolicy(ContextWindowPolicy.IMPORTANCE);
        cfg.setMaxItems(6);
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(cfg);

        CitationConfigDTO citationCfg = new CitationConfigDTO();
        citationCfg.setEnabled(false);
        when(citationConfigService.getConfigOrDefault()).thenReturn(citationCfg);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);

        when(ragRetrievalService.retrieve(eq("q"), eq(6), eq(100L))).thenReturn(List.of(new RagPostChatRetrievalService.Hit()));

        Map<ContextWindowPolicy, Integer> usedByPolicy = Map.of(
                ContextWindowPolicy.IMPORTANCE, 70,
                ContextWindowPolicy.TOPK, 50,
                ContextWindowPolicy.SLIDING, 64,
                ContextWindowPolicy.HYBRID, 83
        );
        when(ragContextPromptService.assemble(eq("q"), any(), any(), eq(citationCfg)))
                .thenAnswer(invocation -> {
                    ContextClipConfigDTO inCfg = invocation.getArgument(2);
                    ContextWindowPolicy p = inCfg.getPolicy();
                    int used = usedByPolicy.getOrDefault(p, 0);
                    return assembled(300, used);
                });

        ContextClipTestService service = new ContextClipTestService(
                contextClipConfigService,
                citationConfigService,
                hybridRetrievalConfigService,
                hybridRagRetrievalService,
                ragRetrievalService,
                ragContextPromptService
        );

        ContextClipTestRequest req = new ContextClipTestRequest();
        req.setQueryText("q");
        req.setBoardId(100L);

        ContextClipTestResponse out = service.test(req);
        assertNotNull(out);
        assertEquals(70, out.getUsedTokens());
        assertEquals(ContextWindowPolicy.IMPORTANCE, out.getConfig().getPolicy());

        List<ContextClipTestResponse.Comparison> comparisons = out.getComparisons();
        assertEquals(4, comparisons.size());
        assertEquals("TOPK", comparisons.get(0).getMode());
        assertEquals("SLIDING", comparisons.get(1).getMode());
        assertEquals("IMPORTANCE", comparisons.get(2).getMode());
        assertEquals("HYBRID", comparisons.get(3).getMode());

        assertEquals(-20, comparisons.get(0).getUsedTokensDiff());
        assertEquals(-6, comparisons.get(1).getUsedTokensDiff());
        assertEquals(0, comparisons.get(2).getUsedTokensDiff());
        assertEquals(13, comparisons.get(3).getUsedTokensDiff());
    }

    @Test
    void test_shouldUseRequestedModesWithDedupAndIgnoreInvalidMode() {
        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setPolicy(ContextWindowPolicy.TOPK);
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(cfg);

        CitationConfigDTO citationCfg = new CitationConfigDTO();
        citationCfg.setEnabled(false);
        when(citationConfigService.getConfigOrDefault()).thenReturn(citationCfg);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);

        when(ragRetrievalService.retrieve(eq("q2"), eq(6), eq(8L))).thenReturn(List.of(new RagPostChatRetrievalService.Hit()));
        when(ragContextPromptService.assemble(eq("q2"), any(), any(), eq(citationCfg)))
                .thenAnswer(invocation -> {
                    ContextClipConfigDTO inCfg = invocation.getArgument(2);
                    if (inCfg.getPolicy() == ContextWindowPolicy.HYBRID) return assembled(310, 66);
                    if (inCfg.getPolicy() == ContextWindowPolicy.TOPK) return assembled(300, 50);
                    return assembled(300, 0);
                });

        ContextClipTestService service = new ContextClipTestService(
                contextClipConfigService,
                citationConfigService,
                hybridRetrievalConfigService,
                hybridRagRetrievalService,
                ragRetrievalService,
                ragContextPromptService
        );

        ContextClipTestRequest req = new ContextClipTestRequest();
        req.setQueryText("q2");
        req.setBoardId(8L);
        req.setModes(List.of("hybrid", "bad_mode", "TOPK", "HYBRID"));

        ContextClipTestResponse out = service.test(req);
        assertNotNull(out);
        assertEquals(2, out.getComparisons().size());
        assertEquals("HYBRID", out.getComparisons().get(0).getMode());
        assertEquals("TOPK", out.getComparisons().get(1).getMode());
    }

    @Test
    void test_shouldFallbackWhenHybridRetrieveThrowsAndAssembleNull() {
        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setPolicy(ContextWindowPolicy.TOPK);
        cfg.setMaxItems(3);
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(cfg);
        CitationConfigDTO citationCfg = new CitationConfigDTO();
        when(citationConfigService.getConfigOrDefault()).thenReturn(citationCfg);
        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);
        doThrow(new RuntimeException("x")).when(hybridRagRetrievalService).retrieve(eq("q3"), eq(5L), eq(hybridCfg), eq(false));
        when(ragContextPromptService.assemble(eq("q3"), any(), any(), eq(citationCfg))).thenReturn(null);

        ContextClipTestService service = new ContextClipTestService(
                contextClipConfigService,
                citationConfigService,
                hybridRetrievalConfigService,
                hybridRagRetrievalService,
                ragRetrievalService,
                ragContextPromptService
        );

        ContextClipTestRequest req = new ContextClipTestRequest();
        req.setQueryText("q3");
        req.setBoardId(5L);
        req.setModes(List.of("bad", " "));
        ContextClipTestResponse out = service.test(req);

        assertNotNull(out);
        assertEquals("", out.getContextPrompt());
        assertEquals(4, out.getComparisons().size());
        assertEquals("TOPK", out.getComparisons().get(0).getMode());
    }

    @Test
    void test_shouldUseHybridResultsAndScoreFallback() {
        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        ContextClipConfigDTO cfg = new ContextClipConfigDTO();
        cfg.setPolicy(ContextWindowPolicy.HYBRID);
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(cfg);
        CitationConfigDTO citationCfg = new CitationConfigDTO();
        when(citationConfigService.getConfigOrDefault()).thenReturn(citationCfg);
        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);

        HybridRagRetrievalService.DocHit h = new HybridRagRetrievalService.DocHit();
        h.setScore(0.11);
        h.setFusedScore(0.22);
        h.setRerankScore(0.33);
        h.setTitle("t");
        HybridRagRetrievalService.DocHit h2 = new HybridRagRetrievalService.DocHit();
        h2.setScore(0.44);
        h2.setFusedScore(0.55);
        HybridRagRetrievalService.RetrieveResult rr = new HybridRagRetrievalService.RetrieveResult();
        rr.setFinalHits(Arrays.asList(h, null, h2));
        when(hybridRagRetrievalService.retrieve(eq("q4"), eq(6L), eq(hybridCfg), eq(false))).thenReturn(rr);
        when(ragContextPromptService.assemble(eq("q4"), any(), any(), eq(citationCfg))).thenReturn(assembled(111, 77));

        ContextClipTestService service = new ContextClipTestService(
                contextClipConfigService,
                citationConfigService,
                hybridRetrievalConfigService,
                hybridRagRetrievalService,
                ragRetrievalService,
                ragContextPromptService
        );

        ContextClipTestRequest req = new ContextClipTestRequest();
        req.setQueryText("q4");
        req.setBoardId(6L);
        req.setModes(List.of("HYBRID"));
        ContextClipTestResponse out = service.test(req);

        assertEquals(77, out.getUsedTokens());
        assertEquals(1, out.getComparisons().size());
        assertEquals("HYBRID", out.getComparisons().get(0).getMode());
    }

    @Test
    void test_shouldCoverNullConfigAndItemMappingBranches() {
        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        when(contextClipConfigService.getConfigOrDefault()).thenReturn(null);
        when(citationConfigService.getConfigOrDefault()).thenReturn(new CitationConfigDTO());
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(null);
        when(ragRetrievalService.retrieve(eq(null), eq(6), eq((Long) null))).thenReturn(List.of());

        RagContextPromptService.Item item = new RagContextPromptService.Item();
        item.setRank(1);
        item.setTitle("t");
        item.setTokens(9);
        RagContextPromptService.AssembleResult result = new RagContextPromptService.AssembleResult();
        result.setBudgetTokens(null);
        result.setUsedTokens(null);
        result.setContextPrompt(null);
        result.setSelected(Arrays.asList(item, null));
        result.setDropped(Arrays.asList((RagContextPromptService.Item) null));
        when(ragContextPromptService.assemble(eq(null), any(), any(), any())).thenReturn(result);

        ContextClipTestService service = new ContextClipTestService(
                contextClipConfigService,
                citationConfigService,
                hybridRetrievalConfigService,
                hybridRagRetrievalService,
                ragRetrievalService,
                ragContextPromptService
        );

        ContextClipTestResponse out = service.test(null);
        assertNotNull(out);
        assertEquals("", out.getQueryText());
        assertNull(out.getBoardId());
        assertEquals(0, out.getUsedTokens());
        assertEquals("", out.getContextPrompt());
        assertEquals(1, out.getItemsSelected());
        assertEquals(0, out.getItemsDropped());
        assertEquals(4, out.getComparisons().size());
    }

    @Test
    void privateHelpers_shouldCoverPolicyAndHitMappingBranches() throws Exception {
        Method normalizePolicy = ContextClipTestService.class.getDeclaredMethod("normalizePolicy", String.class);
        normalizePolicy.setAccessible(true);
        assertNull(normalizePolicy.invoke(null, (Object) null));
        assertNull(normalizePolicy.invoke(null, "   "));
        assertNull(normalizePolicy.invoke(null, "bad"));
        assertEquals(ContextWindowPolicy.TOPK, normalizePolicy.invoke(null, "TOPK"));

        Method toRagHits = ContextClipTestService.class.getDeclaredMethod("toRagHits", List.class);
        toRagHits.setAccessible(true);
        assertEquals(0, ((List<?>) toRagHits.invoke(null, (Object) null)).size());
        HybridRagRetrievalService.DocHit h = new HybridRagRetrievalService.DocHit();
        h.setScore(0.9);
        h.setFusedScore(null);
        h.setRerankScore(null);
        List<?> out = (List<?>) toRagHits.invoke(null, Arrays.asList(null, h));
        assertEquals(1, out.size());
    }

    @Test
    void test_shouldUseCustomConfigBranchAndHandleHybridNullHits() {
        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        ContextClipConfigDTO reqCfg = new ContextClipConfigDTO();
        reqCfg.setPolicy(ContextWindowPolicy.SLIDING);
        ContextClipConfigDTO normalizedCfg = new ContextClipConfigDTO();
        normalizedCfg.setPolicy(ContextWindowPolicy.SLIDING);
        when(contextClipConfigService.normalizeConfig(reqCfg)).thenReturn(normalizedCfg);
        when(citationConfigService.getConfigOrDefault()).thenReturn(new CitationConfigDTO());
        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);

        HybridRagRetrievalService.RetrieveResult rr = new HybridRagRetrievalService.RetrieveResult();
        rr.setFinalHits(null);
        when(hybridRagRetrievalService.retrieve(eq("q5"), eq(9L), eq(hybridCfg), eq(false))).thenReturn(rr);
        when(ragContextPromptService.assemble(eq("q5"), any(), any(), any())).thenReturn(assembled(50, 10));

        ContextClipTestService service = new ContextClipTestService(
                contextClipConfigService,
                citationConfigService,
                hybridRetrievalConfigService,
                hybridRagRetrievalService,
                ragRetrievalService,
                ragContextPromptService
        );

        ContextClipTestRequest req = new ContextClipTestRequest();
        req.setUseSavedConfig(false);
        req.setConfig(reqCfg);
        req.setQueryText("q5");
        req.setBoardId(9L);
        req.setModes(List.of("sliding"));

        ContextClipTestResponse out = service.test(req);
        verify(contextClipConfigService).normalizeConfig(reqCfg);
        assertEquals(1, out.getComparisons().size());
        assertEquals("SLIDING", out.getComparisons().get(0).getMode());
    }

    @Test
    void privateHelpers_shouldCoverResolveModesAndCloneConfigBranches() throws Exception {
        Method resolveModes = ContextClipTestService.class.getDeclaredMethod("resolveModes", ContextClipTestRequest.class);
        resolveModes.setAccessible(true);
        ContextClipTestRequest reqNullModes = new ContextClipTestRequest();
        reqNullModes.setModes(null);
        List<?> m1 = (List<?>) resolveModes.invoke(null, reqNullModes);
        assertEquals(4, m1.size());
        ContextClipTestRequest reqEmptyModes = new ContextClipTestRequest();
        reqEmptyModes.setModes(List.of());
        List<?> m2 = (List<?>) resolveModes.invoke(null, reqEmptyModes);
        assertEquals(4, m2.size());

        Method cloneConfig = ContextClipTestService.class.getDeclaredMethod("cloneConfig", ContextClipConfigDTO.class);
        cloneConfig.setAccessible(true);
        ContextClipConfigDTO cloned = (ContextClipConfigDTO) cloneConfig.invoke(null, new Object[]{null});
        assertNotNull(cloned);
        assertNull(cloned.getPolicy());

        Method safeInt = ContextClipTestService.class.getDeclaredMethod("safeInt", Integer.class);
        safeInt.setAccessible(true);
        assertEquals(0, safeInt.invoke(null, new Object[]{null}));
        assertEquals(5, safeInt.invoke(null, 5));

        Method safeString = ContextClipTestService.class.getDeclaredMethod("safeString", String.class);
        safeString.setAccessible(true);
        assertEquals("", safeString.invoke(null, new Object[]{null}));
        assertEquals("x", safeString.invoke(null, "x"));
        assertTrue(true);
    }

    private static RagContextPromptService.AssembleResult assembled(int budgetTokens, int usedTokens) {
        RagContextPromptService.AssembleResult result = new RagContextPromptService.AssembleResult();
        result.setBudgetTokens(budgetTokens);
        result.setUsedTokens(usedTokens);
        result.setContextPrompt("p-" + usedTokens);
        result.setSelected(List.of());
        result.setDropped(List.of());
        return result;
    }
}
