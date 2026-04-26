package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HybridRagRetrievalServiceFuseRrfBranchTest {

    @Test
    void fuse_invalidModeFallsBackToRrf_andTruncates() throws Exception {
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
        bm25.add(doc("A", 10.0));
        bm25.add(doc(null, 99.0));
        bm25.add(null);
        bm25.add(doc("B", 9.0));
        List<HybridRagRetrievalService.DocHit> vec = new ArrayList<>();
        vec.add(doc("B", 8.0));
        vec.add(doc(null, 98.0));
        vec.add(null);
        vec.add(doc("C", 7.0));

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setFusionMode("BAD");
        cfg.setRrfK(1);
        cfg.setBm25Weight(1.0);
        cfg.setVecWeight(1.0);

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("fuse", List.class, List.class, List.class, HybridRetrievalConfigDTO.class, int.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> out = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, bm25, vec, null, cfg, 2);

        assertEquals(2, out.size());
        assertEquals("B", out.get(0).getDocId());
        assertEquals("A", out.get(1).getDocId());
        assertTrue(out.get(0).getFusedScore() > out.get(1).getFusedScore());
    }

    @Test
    void lambda_fuse_comparatorHandlesNullScores() throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod(
                "compareFusedScoreDesc",
                HybridRagRetrievalService.DocHit.class,
                HybridRagRetrievalService.DocHit.class
        );
        m.setAccessible(true);

        HybridRagRetrievalService.DocHit a = new HybridRagRetrievalService.DocHit();
        a.setFusedScore(1.0);
        HybridRagRetrievalService.DocHit b = new HybridRagRetrievalService.DocHit();
        b.setFusedScore(2.0);
        int r1 = (int) m.invoke(null, a, b);
        assertTrue(r1 > 0);

        HybridRagRetrievalService.DocHit a2 = new HybridRagRetrievalService.DocHit();
        a2.setFusedScore(null);
        HybridRagRetrievalService.DocHit b2 = new HybridRagRetrievalService.DocHit();
        b2.setFusedScore(2.0);
        int r2 = (int) m.invoke(null, a2, b2);
        assertTrue(r2 > 0);

        HybridRagRetrievalService.DocHit a3 = new HybridRagRetrievalService.DocHit();
        a3.setFusedScore(1.0);
        HybridRagRetrievalService.DocHit b3 = new HybridRagRetrievalService.DocHit();
        b3.setFusedScore(null);
        int r3 = (int) m.invoke(null, a3, b3);
        assertTrue(r3 < 0);
    }

    private static HybridRagRetrievalService.DocHit doc(String id, Double score) {
        HybridRagRetrievalService.DocHit h = new HybridRagRetrievalService.DocHit();
        h.setDocId(id);
        h.setScore(score);
        return h;
    }
}
