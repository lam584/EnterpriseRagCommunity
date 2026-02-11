package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.service.ai.AiRerankService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HybridRagRetrievalServiceRerankMappingTest {

    @Test
    void rerank_appliesIndexOrderAndSetsRanks() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.rerankOnceRouted(any(), any(), any(), any(), anyList(), any(), any(), any(), any()))
                .thenReturn(new AiRerankService.RerankResult(
                        List.of(
                                new AiRerankService.RerankHit(1, 0.9),
                                new AiRerankService.RerankHit(0, 0.1)
                        ),
                        10,
                        "aliyun",
                        "qwen3-rerank"
                ));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                mock(AiRerankService.class),
                llmGateway,
                null,
                null
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setRerankModel("qwen3-rerank");
        cfg.setPerDocMaxTokens(4000);
        cfg.setMaxInputTokens(30_000);

        List<HybridRagRetrievalService.DocHit> fused = List.of(
                doc("A"),
                doc("B"),
                doc("C")
        );

        Method m = HybridRagRetrievalService.class.getDeclaredMethod(
                "rerank",
                String.class,
                List.class,
                int.class,
                HybridRetrievalConfigDTO.class
        );
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> out = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, "q", fused, 3, cfg);

        assertEquals("B", out.get(0).getDocId());
        assertEquals("A", out.get(1).getDocId());
        assertEquals("C", out.get(2).getDocId());

        assertEquals(1, out.get(0).getRerankRank());
        assertEquals(0.9, out.get(0).getRerankScore(), 1e-9);
        assertEquals(2, out.get(1).getRerankRank());
        assertEquals(0.1, out.get(1).getRerankScore(), 1e-9);
        assertEquals(3, out.get(2).getRerankRank());
        assertNull(out.get(2).getRerankScore());
    }

    private static HybridRagRetrievalService.DocHit doc(String id) {
        HybridRagRetrievalService.DocHit h = new HybridRagRetrievalService.DocHit();
        h.setDocId(id);
        h.setTitle(id);
        h.setContentText("text " + id);
        h.setFusedScore(1.0);
        return h;
    }
}
