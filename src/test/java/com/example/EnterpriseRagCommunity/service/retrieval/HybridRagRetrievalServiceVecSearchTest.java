package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridRagRetrievalServiceVecSearchTest {

    @Test
    void vecSearch_shouldThrowIllegalState_whenEmbeddingThrows() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("q")))
                .thenThrow(new RuntimeException("boom"));

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

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokeVecSearch(svc, "q", null, 8));
        assertTrue(ex.getMessage().contains("Embedding failed: boom"));
    }

    @Test
    void vecSearch_shouldReturnEmptyList_whenEmbeddingVectorEmpty() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("q")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[0], 0, "em1"));

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

    @Test
    void vecSearch_shouldThrow_whenConfiguredDimsMismatch() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");
        ragProps.getEs().setEmbeddingDims(5);

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("q")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

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

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokeVecSearch(svc, "q", null, 8));
        assertTrue(ex.getMessage().contains("Embedding dims mismatch: configured=5"));
    }

    @Test
    void vecSearch_shouldThrowIllegalState_whenEnsureIndexFails() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");
        ragProps.getEs().setEmbeddingDims(3);

        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        doThrow(new RuntimeException("idx boom")).when(indexService).ensureIndex(eq("idx_posts"), eq(3));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("q")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                indexService,
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                llmGateway,
                mock(PostsRepository.class),
                mock(SystemConfigurationService.class),
                mock(DependencyIsolationGuard.class),
                mock(DependencyCircuitBreakerService.class)
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokeVecSearch(svc, "q", null, 8));
        assertTrue(ex.getMessage().contains("Ensure ES index failed: idx boom"));
    }

    @Test
    void vecSearch_shouldReturnFilteredHits_whenOk() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":0.5,"_source":{"post_id":10,"chunk_index":0,"board_id":3,"title":"t","content_text":"c1"}},
                  {"_id":"d2","_score":0.4,"_source":{"post_id":11,"chunk_index":1,"board_id":3,"title":"t2","content_text":"c2"}}
                ]}}
                """);

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");
        ragProps.getEs().setEmbeddingDims(0);

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

        PostsEntity p10 = new PostsEntity();
        p10.setId(10L);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p10));

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

        List<HybridRagRetrievalService.DocHit> out = invokeVecSearch(svc, "q", 3L, 8);
        assertEquals(1, out.size());
        assertEquals("d1", out.get(0).getDocId());

        verify(indexService).ensureIndex(eq("idx_posts"), eq(3));
        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertTrue(req.url().toString().contains("/idx_posts/_search"));
        assertEquals("ApiKey k1", req.headers().get("Authorization"));
        String body = new String(req.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"knn\""));
        assertTrue(body.contains("\"query_vector\""));
        assertTrue(body.contains("\"board_id\""));
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
}
