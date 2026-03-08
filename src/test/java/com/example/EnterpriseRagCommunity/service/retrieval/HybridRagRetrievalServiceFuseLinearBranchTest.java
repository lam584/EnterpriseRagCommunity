package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HybridRagRetrievalServiceFuseLinearBranchTest {

    @Test
    void fuse_linearHandlesNullScores_andDegenerateMinMax() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<HybridRagRetrievalService.DocHit> bm25 = new ArrayList<>();
        bm25.add(doc("A", null));
        bm25.add(doc("B", 1.0));
        bm25.add(doc(null, 9.0));
        bm25.add(null);
        List<HybridRagRetrievalService.DocHit> vec = new ArrayList<>();
        vec.add(doc("A", 2.0));
        vec.add(doc("C", null));
        vec.add(doc(null, 8.0));
        vec.add(null);

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setFusionMode(" linear ");
        cfg.setBm25Weight(1.0);
        cfg.setVecWeight(1.0);

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("fuse", List.class, List.class, List.class, HybridRetrievalConfigDTO.class, int.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> out = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, bm25, vec, null, cfg, 10);

        assertNotNull(out);
        assertEquals(3, out.size());
        for (HybridRagRetrievalService.DocHit h : out) {
            assertNotNull(h.getFusedScore());
        }
    }

    private static HybridRagRetrievalService.DocHit doc(String id, Double score) {
        HybridRagRetrievalService.DocHit h = new HybridRagRetrievalService.DocHit();
        h.setDocId(id);
        h.setScore(score);
        return h;
    }
}
