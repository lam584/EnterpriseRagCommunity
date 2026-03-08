package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagPostChatRetrievalServiceTest {

    @Test
    void retrieve_shouldReturnFilteredHitsAndSendAuthHeader_whenSearchSucceeds() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":0.8,"_source":{"post_id":1,"chunk_index":2,"board_id":42,"title":"t1","content_text":"c1"}},
                  {"_id":"d2","_score":0.4,"_source":{"post_id":2,"chunk_index":3,"board_id":42,"title":"t2","content_text":"c2"}},
                  {"_id":"d3","_source":{"title":"t3","content_text":"c3"}}
                ]}}
                """);

        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 3);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/,mockhttp://backup");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        PostsEntity p1 = new PostsEntity();
        p1.setId(1L);
        p1.setStatus(PostStatus.PUBLISHED);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p1));

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, objectMapper, postsRepository, systemConfigurationService
        );

        List<RagPostChatRetrievalService.Hit> out = svc.retrieve("hello", 5, 42L);
        assertNotNull(out);
        assertEquals(1, out.size());
        assertEquals("d1", out.get(0).getDocId());
        assertEquals(1L, out.get(0).getPostId());
        assertEquals(2, out.get(0).getChunkIndex());
        assertEquals(42L, out.get(0).getBoardId());

        verify(indexService).ensureIndex("idx_posts", 3);
        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertTrue(req.url().toString().contains("/idx_posts/_search?filter_path="));
        assertEquals("ApiKey k1", req.headers().get("Authorization"));
        String body = new String(req.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"size\":5"));
        assertTrue(body.contains("\"k\":5"));
        assertTrue(body.contains("\"num_candidates\":100"));
        assertTrue(body.contains("\"term\":{\"board_id\":42}"));
    }

    @Test
    void retrieve_shouldReturnEmpty_whenQueryTextIsNullOrBlank() {
        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 3);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        assertTrue(svc.retrieve(null, 8, 1L).isEmpty());
        assertTrue(svc.retrieve("   ", 8, 1L).isEmpty());
        verifyNoInteractions(llmGateway);
        verifyNoInteractions(indexService);
        verifyNoInteractions(postsRepository);
        verifyNoInteractions(systemConfigurationService);
    }

    @Test
    void retrieve_shouldReturnEmpty_whenEmbeddingVectorIsNullOrEmpty() throws Exception {
        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 3);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(null);
        assertTrue(svc.retrieve("hello", 8, 1L).isEmpty());

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello2")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{}, 0, "em1"));
        assertTrue(svc.retrieve("hello2", 8, 1L).isEmpty());

        verify(indexService, never()).ensureIndex(eq("idx_posts"), eq(3));
    }

    @Test
    void retrieve_shouldWrapEmbeddingException() throws Exception {
        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 3);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenThrow(new RuntimeException("boom"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 8, 1L));
        assertTrue(ex.getMessage().startsWith("Embedding failed: "));
    }

    @Test
    void retrieve_shouldThrow_whenEmbeddingDimsMismatch() throws Exception {
        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 4);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 8, 1L));
        assertTrue(ex.getMessage().contains("Embedding dims mismatch"));
    }

    @Test
    void retrieve_shouldWrapEnsureIndexException() throws Exception {
        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 0);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        doThrow(new RuntimeException("ensure failed")).when(indexService).ensureIndex("idx_posts", 3);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 8, 1L));
        assertTrue(ex.getMessage().startsWith("Ensure ES index failed: "));
    }

    @Test
    void retrieve_shouldClampTopKAndOmitBoardFilter_whenTopKOutOfRangeAndBoardNull() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 3);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("world")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(" ");

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        assertTrue(svc.retrieve("hello", 0, null).isEmpty());
        MockHttpUrl.RequestCapture req1 = MockHttpUrl.pollRequest();
        assertNotNull(req1);
        String body1 = new String(req1.body(), StandardCharsets.UTF_8);
        assertTrue(body1.contains("\"size\":1"));
        assertTrue(body1.contains("\"k\":1"));
        assertTrue(body1.contains("\"num_candidates\":100"));
        assertTrue(!body1.contains("\"term\":{\"board_id\":"));

        assertTrue(svc.retrieve("world", 99, null).isEmpty());
        MockHttpUrl.RequestCapture req2 = MockHttpUrl.pollRequest();
        assertNotNull(req2);
        String body2 = new String(req2.body(), StandardCharsets.UTF_8);
        assertTrue(body2.contains("\"size\":20"));
        assertTrue(body2.contains("\"k\":20"));
        assertTrue(body2.contains("\"num_candidates\":200"));
    }

    @Test
    void retrieve_shouldThrow_whenEsReturnsErrorPayload() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"bad\"}");

        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 3);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 8, 1L));
        assertTrue(ex.getMessage().contains("ES search failed"));
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("ES error HTTP 500"));
    }

    @Test
    void retrieve_shouldReturnEmpty_whenEsErrorStreamIsNull() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, null);

        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 3);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        assertTrue(svc.retrieve("hello", 8, 1L).isEmpty());
    }

    @Test
    void filterVisibleHits_shouldReturnOriginalHits_whenAllHitsHaveNullPostId() throws Exception {
        RetrievalRagProperties ragProps = baseProps("idx_posts", "em1", 3);
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostChatRetrievalService svc = new RagPostChatRetrievalService(
                ragProps, indexService, llmGateway, new ObjectMapper(), postsRepository, systemConfigurationService
        );

        RagPostChatRetrievalService.Hit noPost = new RagPostChatRetrievalService.Hit();
        noPost.setDocId("no-post");
        List<RagPostChatRetrievalService.Hit> allNoPost = java.util.Arrays.asList(null, noPost);
        List<RagPostChatRetrievalService.Hit> same = invokeFilterVisibleHits(svc, allNoPost);
        assertSame(allNoPost, same);
        verify(postsRepository, never()).findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED));
    }

    private static RetrievalRagProperties baseProps(String index, String model, int dims) {
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex(index);
        ragProps.getEs().setEmbeddingModel(model);
        ragProps.getEs().setEmbeddingDims(dims);
        return ragProps;
    }

    private static List<RagPostChatRetrievalService.Hit> invokeFilterVisibleHits(
            RagPostChatRetrievalService svc,
            List<RagPostChatRetrievalService.Hit> hits
    ) throws Exception {
        Method m = RagPostChatRetrievalService.class.getDeclaredMethod("filterVisibleHits", List.class);
        m.setAccessible(true);
        try {
            return (List<RagPostChatRetrievalService.Hit>) m.invoke(svc, hits);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error er) throw er;
            throw e;
        }
    }
}
