package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.AiRerankService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridRagRetrievalServiceFileVecBranchCoverageTest {

    @Test
    void retrieve_shouldSkipFileVec_whenDisabled() {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");

        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                mock(RagPostsIndexService.class),
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                mock(LlmGateway.class),
                ragFileAssetChatRetrievalService,
                mock(PostsRepository.class),
                mock(SystemConfigurationService.class),
                mock(DependencyIsolationGuard.class),
                mock(DependencyCircuitBreakerService.class)
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setFileVecEnabled(false);
        cfg.setFileVecK(3);
        cfg.setHybridK(2);
        cfg.setMaxDocs(5);
        cfg.setRerankEnabled(false);

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", 1L, cfg, true);
        assertNotNull(out);
        assertNotNull(out.getFileVecHits());
        assertTrue(out.getFileVecHits().isEmpty());
        assertNull(out.getFileVecError());
        verify(ragFileAssetChatRetrievalService, never()).retrieve(anyString(), any(Integer.class));
    }

    @Test
    void retrieve_shouldTreatNullFileVecEnabledAsTrue() {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");

        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        when(ragFileAssetChatRetrievalService.retrieve("q", 1)).thenReturn(List.of(rawHit("F1", 0.5, 7L, 0, "f", "c", List.of(11L))));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                mock(RagPostsIndexService.class),
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                mock(LlmGateway.class),
                ragFileAssetChatRetrievalService,
                mock(PostsRepository.class),
                mock(SystemConfigurationService.class),
                mock(DependencyIsolationGuard.class),
                mock(DependencyCircuitBreakerService.class)
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(0);
        cfg.setVecK(0);
        cfg.setFileVecK(1);
        cfg.setHybridK(2);
        cfg.setMaxDocs(5);
        cfg.setRerankEnabled(false);

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", 1L, cfg, false);
        assertNotNull(out);
        assertEquals(1, out.getFileVecHits().size());
        assertEquals("F1", out.getFileVecHits().get(0).getDocId());
        verify(ragFileAssetChatRetrievalService).retrieve("q", 1);
    }

    @Test
    void retrieve_shouldCaptureFileVecError_whenEnabledAndRetrieveFails() {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");

        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        when(ragFileAssetChatRetrievalService.retrieve("q", 2)).thenThrow(new RuntimeException("file vec down"));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                mock(RagPostsIndexService.class),
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                mock(LlmGateway.class),
                ragFileAssetChatRetrievalService,
                mock(PostsRepository.class),
                mock(SystemConfigurationService.class),
                mock(DependencyIsolationGuard.class),
                mock(DependencyCircuitBreakerService.class)
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(0);
        cfg.setVecK(0);
        cfg.setFileVecEnabled(true);
        cfg.setFileVecK(2);
        cfg.setHybridK(2);
        cfg.setMaxDocs(5);
        cfg.setRerankEnabled(false);

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", null, cfg, true);
        assertNotNull(out);
        assertEquals("file vec down", out.getFileVecError());
        assertNotNull(out.getDebugInfo());
        assertEquals("file vec down", out.getDebugInfo().get("fileVecError"));
    }

    @Test
    void retrieve_shouldNotInvokeFileVec_whenFileVecKClampedToZeroByMaxDocs() {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");

        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                mock(RagPostsIndexService.class),
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                mock(LlmGateway.class),
                ragFileAssetChatRetrievalService,
                mock(PostsRepository.class),
                mock(SystemConfigurationService.class),
                mock(DependencyIsolationGuard.class),
                mock(DependencyCircuitBreakerService.class)
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(0);
        cfg.setVecK(0);
        cfg.setFileVecEnabled(true);
        cfg.setFileVecK(5);
        cfg.setMaxDocs(0);
        cfg.setHybridK(1);
        cfg.setRerankEnabled(false);

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", 1L, cfg, true);
        assertNotNull(out);
        assertNotNull(out.getFileVecHits());
        assertTrue(out.getFileVecHits().isEmpty());
        assertNull(out.getFileVecError());
        verify(ragFileAssetChatRetrievalService, never()).retrieve(anyString(), any(Integer.class));
    }

    @Test
    void fileVecSearch_shouldMapFields_andFilterInvalidRows() throws Exception {
        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        when(ragFileAssetChatRetrievalService.retrieve("q", 4)).thenReturn(java.util.Arrays.asList(
                null,
                rawHit(null, 1.0, 100L, 0, "f0", "c0", List.of(10L)),
                rawHit("D1", 0.9, 101L, 1, "f1", "c1", List.of()),
                rawHit("D2", 0.8, 102L, 2, "f2", "c2", List.of(99L, 100L))
        ));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                ragFileAssetChatRetrievalService,
                null,
                null,
                null,
                null
        );

        List<HybridRagRetrievalService.DocHit> out = invokeFileVecSearch(svc, "q", 4);
        assertEquals(2, out.size());
        assertEquals("D1", out.get(0).getDocId());
        assertNull(out.get(0).getPostId());
        assertEquals("FILE_ASSET", out.get(0).getSourceType());
        assertEquals("D2", out.get(1).getDocId());
        assertEquals(99L, out.get(1).getPostId());
        assertEquals(List.of(99L, 100L), out.get(1).getPostIds());
    }

    @Test
    void fileVecSearch_shouldReturnEmpty_whenRawHitsNullOrEmpty() throws Exception {
        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        when(ragFileAssetChatRetrievalService.retrieve("q", 4)).thenReturn(null);
        when(ragFileAssetChatRetrievalService.retrieve("q", 5)).thenReturn(List.of());

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                ragFileAssetChatRetrievalService,
                null,
                null,
                null,
                null
        );

        List<HybridRagRetrievalService.DocHit> outNull = invokeFileVecSearch(svc, "q", 4);
        assertNotNull(outNull);
        assertTrue(outNull.isEmpty());

        List<HybridRagRetrievalService.DocHit> outEmpty = invokeFileVecSearch(svc, "q", 5);
        assertNotNull(outEmpty);
        assertTrue(outEmpty.isEmpty());
    }

    @Test
    void fuse_shouldCoverFileVecLinearAndRrfPaths() throws Exception {
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
                null,
                null
        );

        HybridRetrievalConfigDTO linearCfg = new HybridRetrievalConfigDTO();
        linearCfg.setFusionMode("LINEAR");
        linearCfg.setFileVecWeight(2.0);
        List<HybridRagRetrievalService.DocHit> linearOut = invokeFuse(
                svc,
                null,
                null,
                new ArrayList<>(java.util.Arrays.asList(doc("F1", 1.0), doc("F2", 3.0), doc(null, 4.0), null)),
                linearCfg,
                10
        );
        assertEquals(2, linearOut.size());
        assertNotNull(linearOut.get(0).getFusedScore());
        assertNotNull(linearOut.get(1).getFusedScore());

        HybridRetrievalConfigDTO rrfCfg = new HybridRetrievalConfigDTO();
        rrfCfg.setFusionMode("RRF");
        rrfCfg.setRrfK(1);
        rrfCfg.setBm25Weight(1.0);
        rrfCfg.setVecWeight(1.0);
        rrfCfg.setFileVecWeight(1.5);
        List<HybridRagRetrievalService.DocHit> rrfOut = invokeFuse(
                svc,
                List.of(doc("A", 9.0), doc("B", 8.0)),
                List.of(doc("B", 7.0)),
                List.of(doc("B", 6.0)),
                rrfCfg,
                10
        );
        assertEquals("B", rrfOut.get(0).getDocId());
        assertTrue(rrfOut.get(0).getFusedScore() > rrfOut.get(1).getFusedScore());
    }

    @Test
    void rerank_shouldUseDefaultConfigBranches_whenQueryIsNull() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.rerankOnceRouted(
                eq(LlmQueueTaskType.RERANK),
                eq(null),
                eq(null),
                eq(null),
                anyList(),
                eq(1),
                anyString(),
                eq(false),
                eq(null)
        )).thenReturn(new AiRerankService.RerankResult(
                List.of(new AiRerankService.RerankHit(0, 0.77)),
                5,
                "aliyun",
                "qwen3-rerank"
        ));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                llmGateway,
                null,
                null,
                null,
                null,
                null
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setRerankModel("   ");

        List<HybridRagRetrievalService.DocHit> reranked = invokeRerank(svc, null, List.of(docWithText("A", "标题", "正文")), 1, cfg);
        assertEquals(1, reranked.size());
        assertEquals("A", reranked.get(0).getDocId());
        assertEquals(1, reranked.get(0).getRerankRank());
        assertEquals(0.77, reranked.get(0).getRerankScore(), 1e-9);
    }

    @Test
    void vecSearch_shouldReturnEmpty_whenEmbeddingResultIsNull() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("q")))
                .thenReturn(null);

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                mock(RagPostsIndexService.class),
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                llmGateway,
                mock(PostsRepository.class),
                mock(SystemConfigurationService.class),
                mock(DependencyIsolationGuard.class),
                mock(DependencyCircuitBreakerService.class)
        );

        List<HybridRagRetrievalService.DocHit> out = invokeVecSearch(svc, "q", null, 8);
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static List<HybridRagRetrievalService.DocHit> invokeFileVecSearch(HybridRagRetrievalService svc, String queryText, int topK) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("fileVecSearch", String.class, int.class);
        m.setAccessible(true);
        return (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, queryText, topK);
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

    @SuppressWarnings("unchecked")
    private static List<HybridRagRetrievalService.DocHit> invokeRerank(
            HybridRagRetrievalService svc,
            String query,
            List<HybridRagRetrievalService.DocHit> fused,
            int rerankK,
            HybridRetrievalConfigDTO cfg
    ) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("rerank", String.class, List.class, int.class, HybridRetrievalConfigDTO.class);
        m.setAccessible(true);
        try {
            return (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, query, fused, rerankK, cfg);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error er) throw er;
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<HybridRagRetrievalService.DocHit> invokeVecSearch(HybridRagRetrievalService svc, String queryText, Long boardId, int topK) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("vecSearch", String.class, Long.class, int.class);
        m.setAccessible(true);
        try {
            return (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, queryText, boardId, topK);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error er) throw er;
            throw e;
        }
    }

    private static HybridRagRetrievalService.DocHit doc(String id, Double score) {
        HybridRagRetrievalService.DocHit d = new HybridRagRetrievalService.DocHit();
        d.setDocId(id);
        d.setScore(score);
        return d;
    }

    private static HybridRagRetrievalService.DocHit docWithText(String id, String title, String contentText) {
        HybridRagRetrievalService.DocHit d = new HybridRagRetrievalService.DocHit();
        d.setDocId(id);
        d.setTitle(title);
        d.setContentText(contentText);
        return d;
    }

    private static RagFileAssetChatRetrievalService.Hit rawHit(
            String docId,
            Double score,
            Long fileAssetId,
            Integer chunkIndex,
            String fileName,
            String contentText,
            List<Long> postIds
    ) {
        RagFileAssetChatRetrievalService.Hit h = new RagFileAssetChatRetrievalService.Hit();
        h.setDocId(docId);
        h.setScore(score);
        h.setFileAssetId(fileAssetId);
        h.setChunkIndex(chunkIndex);
        h.setFileName(fileName);
        h.setContentText(contentText);
        h.setPostIds(postIds);
        return h;
    }
}
