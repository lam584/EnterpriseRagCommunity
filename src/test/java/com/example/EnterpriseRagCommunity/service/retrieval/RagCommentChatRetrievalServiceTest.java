package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagCommentChatRetrievalServiceTest {

    @Test
    void retrieve_shouldReturnEmpty_whenQueryBlankOrNull() {
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                new RetrievalRagProperties(),
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        assertEquals(List.of(), svc.retrieve(null, 5));
        assertEquals(List.of(), svc.retrieve("   ", 5));

        verifyNoInteractions(indexService, llmGateway, postsRepository, systemConfigurationService);
    }

    @Test
    void retrieve_shouldClampTopK_andUseInferredDims_andBuildVectorBody() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel("embed-v1");
        ragProps.getEs().setEmbeddingDims(0);
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(indexService.defaultIndexName()).thenReturn("idx_comments");
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(null);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v1"), anyString()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1.0f, 2.0f, 3.0f}, 3, "embed-v1"));

        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                ragProps,
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        assertEquals(List.of(), svc.retrieve("hello", 0));
        assertEquals(List.of(), svc.retrieve("hello", 999));
        verify(indexService, times(2)).ensureIndex("idx_comments", 3);

        MockHttpUrl.RequestCapture req1 = MockHttpUrl.pollRequest();
        MockHttpUrl.RequestCapture req2 = MockHttpUrl.pollRequest();
        assertNotNull(req1);
        assertNotNull(req2);

        String body1 = new String(req1.body(), StandardCharsets.UTF_8);
        String body2 = new String(req2.body(), StandardCharsets.UTF_8);
        assertTrue(body1.contains("\"size\":1"));
        assertTrue(body1.contains("\"k\":1"));
        assertTrue(body1.contains("\"num_candidates\":100"));
        assertTrue(body1.contains("\"query_vector\":[1.0,2.0,3.0]"));
        assertTrue(body1.contains("\"highlight\""));
        assertTrue(body1.contains("\"pre_tags\":[\"<em>\"]"));
        assertTrue(body2.contains("\"size\":50"));
        assertTrue(body2.contains("\"k\":50"));
        assertTrue(body2.contains("\"num_candidates\":500"));
    }

    @Test
    void retrieve_shouldHandleEmbeddingExceptionsAndNullVectors() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel("embed-v2");
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                ragProps,
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v2"), eq("q1")))
                .thenThrow(new RuntimeException("embed down"));
        IllegalStateException e1 = assertThrows(IllegalStateException.class, () -> svc.retrieve("q1", 5));
        assertTrue(e1.getMessage().contains("Embedding failed"));

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v2"), eq("q2")))
                .thenReturn(null);
        assertEquals(List.of(), svc.retrieve("q2", 5));

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v2"), eq("q3")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(null, 0, "embed-v2"));
        assertEquals(List.of(), svc.retrieve("q3", 5));

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v2"), eq("q4")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[0], 0, "embed-v2"));
        assertEquals(List.of(), svc.retrieve("q4", 5));

        verify(indexService, never()).ensureIndex(anyString(), anyInt());
    }

    @Test
    void retrieve_shouldThrowOnDimsMismatch_andWrapEnsureIndexFailure() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel("embed-v3");
        ragProps.getEs().setEmbeddingDims(2);
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(indexService.defaultIndexName()).thenReturn("idx_comments");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v3"), eq("q1")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1.0f, 2.0f, 3.0f}, 3, "embed-v3"));

        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                ragProps,
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        IllegalStateException e1 = assertThrows(IllegalStateException.class, () -> svc.retrieve("q1", 5));
        assertTrue(e1.getMessage().contains("Embedding dims mismatch"));

        ragProps.getEs().setEmbeddingDims(3);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v3"), eq("q2")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1.0f, 2.0f, 3.0f}, 3, "embed-v3"));
        doThrow(new RuntimeException("idx failed")).when(indexService).ensureIndex("idx_comments", 3);

        IllegalStateException e2 = assertThrows(IllegalStateException.class, () -> svc.retrieve("q2", 5));
        assertTrue(e2.getMessage().contains("Ensure ES index failed"));
    }

    @Test
    void retrieve_shouldReturnEmpty_whenHitsNodeIsNotArray() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":{}}}");

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel("embed-v4");
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(indexService.defaultIndexName()).thenReturn("idx_comments");
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(null);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v4"), eq("q")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1.0f}, 1, "embed-v4"));

        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                ragProps,
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        assertEquals(List.of(), svc.retrieve("q", 1));
        verify(postsRepository, never()).findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED));
    }

    @Test
    void retrieve_shouldMapOptionalFields_andFilterByVisiblePosts() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":1.2,"highlight":{"content_text":["<em>alpha</em> beta"]},"_source":{"comment_id":10,"post_id":100,"chunk_index":3,"content_text":"alpha"}},
                  {"_id":"d2","_source":{"post_id":101}},
                  {"_id":"d3","_score":0.4,"_source":{"comment_id":12,"chunk_index":9,"content_text":"no-post"}}
                ]}}
                """);

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel("embed-v5");
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(indexService.defaultIndexName()).thenReturn("idx_comments");
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(null);
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("embed-v5"), eq("q")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1.0f}, 1, "embed-v5"));
        PostsEntity visible = new PostsEntity();
        visible.setId(100L);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(eq(List.of(100L, 101L)), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(visible));

        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                ragProps,
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        List<RagCommentChatRetrievalService.Hit> out = svc.retrieve("q", 1);
        assertEquals(1, out.size());
        RagCommentChatRetrievalService.Hit h = out.get(0);
        assertEquals("d1", h.getDocId());
        assertEquals(1.2, h.getScore());
        assertEquals(10L, h.getCommentId());
        assertEquals(100L, h.getPostId());
        assertEquals(3, h.getChunkIndex());
        assertEquals("alpha", h.getContentText());
        assertEquals("<em>alpha</em> beta", h.getContentHighlight());
    }

    @Test
    void filterVisiblePosts_shouldCoverNullAndFilteringBranches() throws Exception {
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                new RetrievalRagProperties(),
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        assertEquals(List.of(), invokeFilterVisiblePosts(svc, null));
        assertEquals(List.of(), invokeFilterVisiblePosts(svc, List.of()));

        RagCommentChatRetrievalService.Hit noPost = new RagCommentChatRetrievalService.Hit();
        noPost.setDocId("no-post");
        List<RagCommentChatRetrievalService.Hit> allNoPost = java.util.Arrays.asList(null, noPost);
        List<RagCommentChatRetrievalService.Hit> same = invokeFilterVisiblePosts(svc, allNoPost);
        assertSame(allNoPost, same);
        verify(postsRepository, never()).findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED));

        RagCommentChatRetrievalService.Hit h1 = new RagCommentChatRetrievalService.Hit();
        h1.setDocId("h1");
        h1.setPostId(1L);
        RagCommentChatRetrievalService.Hit h2 = new RagCommentChatRetrievalService.Hit();
        h2.setDocId("h2");
        h2.setPostId(2L);
        RagCommentChatRetrievalService.Hit h3 = new RagCommentChatRetrievalService.Hit();
        h3.setDocId("h3");
        h3.setPostId(null);

        PostsEntity nullId = new PostsEntity();
        nullId.setId(null);
        PostsEntity visible = new PostsEntity();
        visible.setId(2L);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(eq(List.of(1L, 2L)), eq(PostStatus.PUBLISHED)))
                .thenReturn(java.util.Arrays.asList(null, nullId, visible));

        List<RagCommentChatRetrievalService.Hit> filtered = invokeFilterVisiblePosts(svc, java.util.Arrays.asList(h1, h2, h3, null));
        assertEquals(1, filtered.size());
        assertEquals("h2", filtered.get(0).getDocId());
    }

    @Test
    void postSearch_shouldNormalizeEndpoint_setApiKey_andHandleErrorBranches() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");
        MockHttpUrl.enqueue(500, null);
        MockHttpUrl.enqueue(400, "{\"error\":\"bad\"}");

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es-one:9200/ , mockhttp://es-two:9200");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY"))
                .thenReturn("  token-1  ");

        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                ragProps,
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        JsonNode r1 = invokePostSearch(svc, "idx_comments", "{\"size\":1}");
        assertNotNull(r1);
        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertTrue(req.url().toString().startsWith("mockhttp://es-one:9200/idx_comments/_search"));
        assertEquals("ApiKey token-1", req.headers().get("Authorization"));

        JsonNode r2 = invokePostSearch(svc, "idx_comments", "{\"size\":1}");
        assertNotNull(r2);
        assertTrue(r2.isObject());
        assertEquals(0, r2.size());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokePostSearch(svc, "idx_comments", "{\"size\":1}"));
        assertTrue(ex.getMessage().contains("ES search failed: ES error HTTP 400"));
    }

    @Test
    void postSearch_shouldFallbackToLocalEndpoint_andWrapMalformedEndpoint() throws Exception {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagCommentChatRetrievalService svc = new RagCommentChatRetrievalService(
                ragProps,
                indexService,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn(" ");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(null);
        IllegalStateException fallbackError = assertThrows(IllegalStateException.class, () -> invokePostSearch(svc, "idx_comments", "{}"));
        assertTrue(fallbackError.getMessage().contains("ES search failed"));

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("://bad-url");
        IllegalStateException malformedError = assertThrows(IllegalStateException.class, () -> invokePostSearch(svc, "idx_comments", "{}"));
        assertTrue(malformedError.getMessage().contains("ES search failed"));
    }

    @Test
    void buildKnnSearchBody_shouldHandleSingleAndMultiDimVectors() throws Exception {
        String oneDim = invokeBuildKnnSearchBody(2, 100, new float[]{1.0f});
        assertTrue(oneDim.contains("\"query_vector\":[1.0]"));
        assertTrue(oneDim.contains("\"k\":2"));

        String multiDim = invokeBuildKnnSearchBody(2, 100, new float[]{1.0f, 2.0f, 3.0f});
        assertTrue(multiDim.contains("\"query_vector\":[1.0,2.0,3.0]"));
        assertTrue(multiDim.contains("\"num_candidates\":100"));
        assertFalse(multiDim.contains(",]"));
    }

    @SuppressWarnings("unchecked")
    private static List<RagCommentChatRetrievalService.Hit> invokeFilterVisiblePosts(
            RagCommentChatRetrievalService svc,
            List<RagCommentChatRetrievalService.Hit> hits
    ) throws Exception {
        Method m = RagCommentChatRetrievalService.class.getDeclaredMethod("filterVisiblePosts", List.class);
        m.setAccessible(true);
        try {
            return (List<RagCommentChatRetrievalService.Hit>) m.invoke(svc, hits);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error er) throw er;
            throw e;
        }
    }

    private static JsonNode invokePostSearch(RagCommentChatRetrievalService svc, String indexName, String body) throws Exception {
        Method m = RagCommentChatRetrievalService.class.getDeclaredMethod("postSearch", String.class, String.class);
        m.setAccessible(true);
        try {
            return (JsonNode) m.invoke(svc, indexName, body);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error er) throw er;
            throw e;
        }
    }

    private static String invokeBuildKnnSearchBody(int size, int numCandidates, float[] vec) throws Exception {
        Method m = RagCommentChatRetrievalService.class.getDeclaredMethod("buildKnnSearchBody", int.class, int.class, float[].class);
        m.setAccessible(true);
        return (String) m.invoke(null, size, numCandidates, vec);
    }
}
