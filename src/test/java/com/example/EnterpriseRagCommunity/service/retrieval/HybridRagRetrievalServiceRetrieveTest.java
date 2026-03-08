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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridRagRetrievalServiceRetrieveTest {

    @Test
    void retrieve_shouldEarlyReturn_whenQueryNull_debugIgnored() {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                mock(RetrievalRagProperties.class),
                mock(RagPostsIndexService.class),
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                mock(LlmGateway.class),
                mock(PostsRepository.class),
                mock(SystemConfigurationService.class),
                mock(DependencyIsolationGuard.class),
                mock(DependencyCircuitBreakerService.class)
        );

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve(null, 1L, null, true);
        assertEquals("", out.getQueryText());
        assertNotNull(out.getFinalHits());
        assertTrue(out.getFinalHits().isEmpty());
        assertNull(out.getDebugInfo());
    }

    @Test
    void retrieve_shouldDoNothingExternal_whenCfgNull() {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                mock(RagPostsIndexService.class),
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                mock(LlmGateway.class),
                mock(PostsRepository.class),
                mock(SystemConfigurationService.class),
                mock(DependencyIsolationGuard.class),
                mock(DependencyCircuitBreakerService.class)
        );

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", 1L, null, false);
        assertNotNull(out.getBm25Hits());
        assertNotNull(out.getVecHits());
        assertNotNull(out.getFusedHits());
        assertNotNull(out.getFinalHits());
        assertTrue(out.getBm25Hits().isEmpty());
        assertTrue(out.getVecHits().isEmpty());
        assertTrue(out.getFusedHits().isEmpty());
        assertTrue(out.getFinalHits().isEmpty());
        assertNotNull(out.getBm25LatencyMs());
        assertNotNull(out.getVecLatencyMs());
        assertNotNull(out.getFuseLatencyMs());
        assertNull(out.getRerankLatencyMs());
        assertNull(out.getDebugInfo());
    }

    @Test
    void retrieve_shouldClampHybridKToAtLeastOne() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":1.0,"_source":{"post_id":10,"chunk_index":0,"board_id":1,"title":"t1","content_text":"c1"}},
                  {"_id":"d2","_score":0.9,"_source":{"post_id":11,"chunk_index":1,"board_id":1,"title":"t2","content_text":"c2"}},
                  {"_id":"d3","_score":0.8,"_source":{"post_id":12,"chunk_index":2,"board_id":1,"title":"t3","content_text":"c3"}}
                ]}}
                """);

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");
        ragProps.getEs().setEmbeddingDims(3);

        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> s = inv.getArgument(1);
            return s.get();
        });
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("q")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        PostsEntity p10 = new PostsEntity();
        p10.setId(10L);
        PostsEntity p11 = new PostsEntity();
        p11.setId(11L);
        PostsEntity p12 = new PostsEntity();
        p12.setId(12L);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p10, p11, p12));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                indexService,
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                llmGateway,
                postsRepository,
                systemConfigurationService,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(3);
        cfg.setVecK(0);
        cfg.setHybridK(0);
        cfg.setMaxDocs(10);
        cfg.setRerankEnabled(false);

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", 1L, cfg, false);

        assertEquals(3, out.getBm25Hits().size());
        assertTrue(out.getVecHits().isEmpty());
        assertEquals(3, out.getFusedHits().size());
        assertEquals(1, out.getFinalHits().size());
        assertNull(out.getDebugInfo());

        verify(indexService, never()).ensureIndex(anyString(), anyInt());
        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertTrue(req.url().toString().contains("/idx_posts/_search"));
        String body = new String(req.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"multi_match\""));
    }

    @Test
    void retrieve_shouldSetRerankError_whenRerankThrows() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":1.0,"_source":{"post_id":10,"chunk_index":0,"board_id":1,"title":"t1","content_text":"c1"}},
                  {"_id":"d2","_score":0.9,"_source":{"post_id":11,"chunk_index":1,"board_id":1,"title":"t2","content_text":"c2"}}
                ]}}
                """);
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":0.7,"_source":{"post_id":10,"chunk_index":0,"board_id":1,"title":"t1","content_text":"c1"}},
                  {"_id":"d2","_score":0.6,"_source":{"post_id":11,"chunk_index":1,"board_id":1,"title":"t2","content_text":"c2"}}
                ]}}
                """);

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");
        ragProps.getEs().setEmbeddingDims(3);

        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> s = inv.getArgument(1);
            return s.get();
        });
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("q")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        when(llmGateway.rerankOnceRouted(any(), any(), any(), any(), anyList(), any(), any(), any(), any()))
                .thenThrow(new IOException("upstream down"));

        PostsEntity p10 = new PostsEntity();
        p10.setId(10L);
        PostsEntity p11 = new PostsEntity();
        p11.setId(11L);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p10, p11));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                indexService,
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                llmGateway,
                postsRepository,
                systemConfigurationService,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(2);
        cfg.setVecK(2);
        cfg.setHybridK(6);
        cfg.setMaxDocs(10);
        cfg.setRerankEnabled(true);
        cfg.setRerankK(2);

        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", 1L, cfg, true);

        assertNotNull(out.getFinalHits());
        assertEquals(2, out.getFinalHits().size());
        assertEquals(2, out.getFusedHits().size());
        assertNotNull(out.getRerankLatencyMs());
        assertNotNull(out.getRerankError());
        assertNotNull(out.getRerankHits());
        assertTrue(out.getRerankHits().isEmpty());
        assertNotNull(out.getDebugInfo());
    }
}
