package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRagRetrievalServiceFuseTest {

    @Test
    void fuse_shouldFallbackToRrf_whenModeInvalid() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setFusionMode("  bad_mode ");
        cfg.setBm25Weight(1.0);
        cfg.setVecWeight(1.0);
        cfg.setRrfK(1);

        List<HybridRagRetrievalService.DocHit> bm25 = List.of(
                hit("A", 10.0),
                hit("B", 9.0),
                hit("C", 8.0)
        );
        List<HybridRagRetrievalService.DocHit> vec = List.of(
                hit("B", 0.9),
                hit("D", 0.8),
                hit("A", 0.7)
        );

        List<HybridRagRetrievalService.DocHit> out = invokeFuse(svc, bm25, vec, null, cfg, 10);
        assertEquals(List.of("B", "A", "D", "C"), out.stream().map(HybridRagRetrievalService.DocHit::getDocId).toList());
        assertEquals(0.9, out.get(0).getVecScore(), 1e-12);
        assertEquals(9.0, out.get(0).getBm25Score(), 1e-12);
        assertEquals(0.8333333333333333, out.get(0).getFusedScore(), 1e-12);
    }

    @Test
    void fuse_shouldSupportLinearMode_andTruncateByMaxDocs() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setFusionMode(" linear ");
        cfg.setBm25Weight(2.0);
        cfg.setVecWeight(1.0);

        List<HybridRagRetrievalService.DocHit> bm25 = List.of(
                hit("A", 10.0),
                hit("B", 0.0),
                hit("C", 5.0)
        );
        List<HybridRagRetrievalService.DocHit> vec = List.of(
                hit("B", 1.0),
                hit("C", 0.5),
                hit("D", 0.2)
        );

        List<HybridRagRetrievalService.DocHit> out = invokeFuse(svc, bm25, vec, null, cfg, 2);
        assertEquals(2, out.size());
        assertEquals("A", out.get(0).getDocId());
        assertEquals("C", out.get(1).getDocId());
        assertNotNull(out.get(0).getFusedScore());
        assertNotNull(out.get(1).getFusedScore());
        assertTrue(out.get(0).getFusedScore() >= out.get(1).getFusedScore());
    }

    @Test
    void fuse_shouldHandleNullLists() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setFusionMode("RRF");

        List<HybridRagRetrievalService.DocHit> out = invokeFuse(svc, null, null, null, cfg, 10);
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    private static HybridRagRetrievalService.DocHit hit(String docId, Double score) {
        HybridRagRetrievalService.DocHit h = new HybridRagRetrievalService.DocHit();
        h.setDocId(docId);
        h.setScore(score);
        return h;
    }

    @SuppressWarnings("unchecked")
    private static List<HybridRagRetrievalService.DocHit> invokeFuse(
            HybridRagRetrievalService svc,
            List<HybridRagRetrievalService.DocHit> bm25,
            List<HybridRagRetrievalService.DocHit> vec,
            List<HybridRagRetrievalService.DocHit> fileVec,
            HybridRetrievalConfigDTO cfg,
            int maxDocs
    ) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("fuse", List.class, List.class, List.class, HybridRetrievalConfigDTO.class, int.class);
        m.setAccessible(true);
        return (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, bm25, vec, fileVec, cfg, maxDocs);
    }
}
