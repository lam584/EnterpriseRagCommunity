package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.AiRerankService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HybridRagRetrievalServiceRetrieveBranchTest {

    @Test
    void retrieve_blankQuery_returnsEmptyFinalHits() {
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

        HybridRagRetrievalService.RetrieveResult out1 = svc.retrieve(null, null, null, false);
        assertNotNull(out1);
        assertNotNull(out1.getFinalHits());
        assertEquals(0, out1.getFinalHits().size());

        HybridRagRetrievalService.RetrieveResult out2 = svc.retrieve("   ", 1L, new HybridRetrievalConfigDTO(), true);
        assertNotNull(out2);
        assertNotNull(out2.getFinalHits());
        assertEquals(0, out2.getFinalHits().size());
        assertNull(out2.getDebugInfo());
    }

    @Test
    void retrieve_recordsBm25AndVecErrors_andBuildsDebugInfo() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx");

        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        doThrow(new RuntimeException("blocked")).when(dependencyIsolationGuard).requireElasticsearchAllowed();

        try {
            when(llmGateway.embedOnceRouted(any(LlmQueueTaskType.class), any(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("embed down"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                indexService,
                embeddingService,
                objectMapper,
                aiRerankService,
                llmGateway,
                postsRepository,
                systemConfigurationService,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(1);
        cfg.setVecK(1);
        cfg.setHybridK(2);
        cfg.setMaxDocs(10);
        cfg.setRerankEnabled(false);

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", null, cfg, true);
        assertNotNull(out);
        assertEquals("blocked", out.getBm25Error());
        assertTrue(out.getVecError().contains("Embedding failed: embed down"));
        assertNotNull(out.getDebugInfo());
        assertEquals("blocked", out.getDebugInfo().get("bm25Error"));
        assertTrue(out.getDebugInfo().get("vecError").toString().contains("Embedding failed: embed down"));
    }

    @Test
    void retrieve_rerankEnabled_truncatesFinalHits_andBuildsDebugInfo() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"D1","_score":10.0,"_source":{"post_id":101,"chunk_index":0,"board_id":1,"title":"T1","content_text":"C1"}},
                  {"_id":"D2","_score":5.0,"_source":{"post_id":102,"chunk_index":1,"board_id":1,"title":"T2","content_text":"C2"}}
                ]}}
                """);

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("m");

        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn("mockhttp://es");
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn(" ");
        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        PostsEntity p1 = new PostsEntity();
        p1.setId(101L);
        PostsEntity p2 = new PostsEntity();
        p2.setId(102L);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED))).thenReturn(List.of(p1, p2));

        when(llmGateway.rerankOnceRouted(any(), any(), any(), any(), anyList(), anyInt(), any(), anyBoolean(), any()))
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
                ragProps,
                indexService,
                embeddingService,
                objectMapper,
                aiRerankService,
                llmGateway,
                postsRepository,
                systemConfigurationService,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(2);
        cfg.setVecK(0);
        cfg.setHybridK(1);
        cfg.setMaxDocs(10);
        cfg.setRerankEnabled(true);
        cfg.setRerankK(2);
        cfg.setRerankModel("qwen3-rerank");
        cfg.setPerDocMaxTokens(4000);
        cfg.setMaxInputTokens(30_000);

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", 1L, cfg, true);
        assertNotNull(out);
        assertNotNull(out.getRerankHits());
        assertEquals(2, out.getRerankHits().size());
        assertEquals("D2", out.getRerankHits().get(0).getDocId());
        assertEquals("D1", out.getRerankHits().get(1).getDocId());

        assertNotNull(out.getFinalHits());
        assertEquals(1, out.getFinalHits().size());
        assertEquals("D2", out.getFinalHits().get(0).getDocId());

        assertNotNull(out.getDebugInfo());
        assertNull(out.getDebugInfo().get("bm25Error"));
        assertNull(out.getDebugInfo().get("vecError"));
    }
}
