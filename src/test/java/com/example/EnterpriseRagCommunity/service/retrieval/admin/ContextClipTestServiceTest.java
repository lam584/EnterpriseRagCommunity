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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
