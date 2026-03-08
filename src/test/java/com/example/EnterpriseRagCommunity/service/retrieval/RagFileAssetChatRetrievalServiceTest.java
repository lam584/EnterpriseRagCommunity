package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagFileAssetChatRetrievalServiceTest {

    @Test
    void retrieve_shouldReturnEmpty_whenQueryBlank() {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        assertTrue(svc.retrieve("  ", 10).isEmpty());
        verifyNoInteractions(llmGateway);
    }

    @Test
    void retrieve_shouldWrapEmbeddingFailure() throws Exception {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenThrow(new RuntimeException("embed-down"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 5));
        assertTrue(ex.getMessage().contains("Embedding failed"));
    }

    @Test
    void retrieve_shouldReturnEmpty_whenEmbeddingVectorMissing() throws Exception {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(null);

        assertTrue(svc.retrieve("hello", 5).isEmpty());
        verifyNoInteractions(indexService);
    }

    @Test
    void retrieve_shouldReturnEmpty_whenEmbeddingVectorEmpty() throws Exception {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{}, 0, "m1"));

        assertTrue(svc.retrieve("hello", 5).isEmpty());
        verifyNoInteractions(indexService);
    }

    @Test
    void retrieve_shouldThrow_whenEmbeddingDimsMismatch() throws Exception {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 5);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 5));
        assertTrue(ex.getMessage().contains("Embedding dims mismatch"));
    }

    @Test
    void retrieve_shouldWrapEnsureIndexFailure() throws Exception {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(indexService.defaultIndexName()).thenReturn("idx_files");
        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));
        doThrow(new RuntimeException("index-down")).when(indexService).ensureIndex("idx_files", 3, true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 5));
        assertTrue(ex.getMessage().contains("Ensure ES index failed"));
    }

    @Test
    void retrieve_shouldSearchAndFilterVisiblePosts_andSetAuthHeader() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"h1","_score":0.93,"_source":{
                    "file_asset_id":11,
                    "chunk_index":3,
                    "file_name":"a.pdf",
                    "mime_type":"application/pdf",
                    "post_ids":[101,102,"x"],
                    "content_text":"alpha"
                  }},
                  {"_id":"h2","_source":{
                    "file_name":"b.txt",
                    "mime_type":"text/plain",
                    "content_text":"beta"
                  }}
                ]}}
                """);

        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 0);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(indexService.defaultIndexName()).thenReturn("idx_files");
        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es1:9200/,mockhttp://es2:9200");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("  key123 ");

        PostsEntity p = new PostsEntity();
        p.setId(101L);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(any(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p));

        List<RagFileAssetChatRetrievalService.Hit> out = svc.retrieve("hello", 0);

        assertEquals(1, out.size());
        assertEquals("h1", out.get(0).getDocId());
        assertEquals(11L, out.get(0).getFileAssetId());
        assertEquals(3, out.get(0).getChunkIndex());
        assertEquals(2, out.get(0).getPostIds().size());
        verify(indexService).ensureIndex("idx_files", 3, true);

        MockHttpUrl.RequestCapture capture = MockHttpUrl.pollRequest();
        assertNotNull(capture);
        assertEquals("POST", capture.method());
        assertEquals("ApiKey key123", capture.headers().get("Authorization"));
        assertEquals("mockhttp://es1:9200/idx_files/_search", capture.url().getProtocol() + "://" + capture.url().getHost() + ":" + capture.url().getPort() + capture.url().getPath());
        String requestBody = new String(capture.body(), StandardCharsets.UTF_8);
        assertTrue(requestBody.contains("\"size\":1"));
        assertTrue(requestBody.contains("\"num_candidates\":100"));
        assertTrue(requestBody.contains("\"query_vector\":[0.1,0.2,0.3]"));
    }

    @Test
    void retrieve_shouldReturnEmpty_whenEsNon2xxWithoutErrorBody() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, null);

        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(indexService.defaultIndexName()).thenReturn("idx_files");
        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");

        assertTrue(svc.retrieve("hello", 5).isEmpty());
    }

    @Test
    void retrieve_shouldThrow_whenEsReturnsErrorBody() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"bad request\"}");

        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(indexService.defaultIndexName()).thenReturn("idx_files");
        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 5));
        assertTrue(ex.getMessage().contains("ES search failed"));
        assertTrue(ex.getMessage().contains("ES error HTTP 500"));
    }

    @Test
    void retrieve_shouldClampTopKTo50_andIgnoreHitsWhenHitsNodeNotArray() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":{\"_id\":\"n1\"}}}");

        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(indexService.defaultIndexName()).thenReturn("idx_files");
        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");

        assertTrue(svc.retrieve("hello", 99).isEmpty());

        MockHttpUrl.RequestCapture capture = MockHttpUrl.pollRequest();
        assertNotNull(capture);
        String requestBody = new String(capture.body(), StandardCharsets.UTF_8);
        assertTrue(requestBody.contains("\"size\":50"));
        assertTrue(requestBody.contains("\"k\":50"));
        assertTrue(requestBody.contains("\"num_candidates\":500"));
    }

    @Test
    void retrieve_shouldThrow_whenEsUriConfigMissing() throws Exception {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(indexService.defaultIndexName()).thenReturn("idx_files");
        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn(null);
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.retrieve("hello", 5));
        assertTrue(ex.getMessage().contains("ES search failed"));
    }

    @Test
    void retrieve_shouldDropHit_whenPostIdsArrayHasNoNumericValues() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"h1","_source":{
                    "file_name":"a.pdf",
                    "mime_type":"application/pdf",
                    "post_ids":["x","y"],
                    "content_text":"alpha"
                  }}
                ]}}
                """);

        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        when(indexService.defaultIndexName()).thenReturn("idx_files");
        when(llmGateway.embedOnceRouted(any(), eq(null), eq("text-embedding-3-small"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");

        assertTrue(svc.retrieve("hello", 5).isEmpty());
        verifyNoInteractions(postsRepository);
    }

    @Test
    void filterVisiblePosts_shouldHandleNullAndUnpublishedInputs() throws Exception {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        RagFileAssetChatRetrievalService.Hit h1 = null;
        RagFileAssetChatRetrievalService.Hit h2 = new RagFileAssetChatRetrievalService.Hit();
        h2.setPostIds(null);
        RagFileAssetChatRetrievalService.Hit h3 = new RagFileAssetChatRetrievalService.Hit();
        h3.setPostIds(List.of());
        RagFileAssetChatRetrievalService.Hit h4 = new RagFileAssetChatRetrievalService.Hit();
        h4.setDocId("keep");
        h4.setPostIds(Arrays.asList(100L, null));
        RagFileAssetChatRetrievalService.Hit h5 = new RagFileAssetChatRetrievalService.Hit();
        h5.setDocId("drop");
        h5.setPostIds(List.of(200L));

        PostsEntity ok = new PostsEntity();
        ok.setId(100L);
        PostsEntity nullId = new PostsEntity();
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(any(), eq(PostStatus.PUBLISHED)))
                .thenReturn(Arrays.asList(ok, null, nullId));

        Method m = RagFileAssetChatRetrievalService.class.getDeclaredMethod("filterVisiblePosts", List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<RagFileAssetChatRetrievalService.Hit> out =
                (List<RagFileAssetChatRetrievalService.Hit>) m.invoke(svc, Arrays.asList(h1, h2, h3, h4, h5));

        assertEquals(1, out.size());
        assertEquals("keep", out.get(0).getDocId());
    }

    @Test
    void filterVisiblePosts_shouldReturnEmpty_whenNoValidPostIds() throws Exception {
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        RetrievalRagProperties ragProps = baseProps("text-embedding-3-small", 3);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagFileAssetChatRetrievalService svc = newService(indexService, ragProps, llmGateway, postsRepository, systemConfigurationService);

        RagFileAssetChatRetrievalService.Hit h1 = null;
        RagFileAssetChatRetrievalService.Hit h2 = new RagFileAssetChatRetrievalService.Hit();
        h2.setPostIds(null);
        RagFileAssetChatRetrievalService.Hit h3 = new RagFileAssetChatRetrievalService.Hit();
        h3.setPostIds(Arrays.asList(null, null));

        Method m = RagFileAssetChatRetrievalService.class.getDeclaredMethod("filterVisiblePosts", List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<RagFileAssetChatRetrievalService.Hit> out =
                (List<RagFileAssetChatRetrievalService.Hit>) m.invoke(svc, Arrays.asList(h1, h2, h3));

        assertTrue(out.isEmpty());
        verifyNoInteractions(postsRepository);
    }

    private static RagFileAssetChatRetrievalService newService(
            RagFileAssetsIndexService indexService,
            RetrievalRagProperties ragProps,
            LlmGateway llmGateway,
            PostsRepository postsRepository,
            SystemConfigurationService systemConfigurationService
    ) {
        return new RagFileAssetChatRetrievalService(
                indexService,
                ragProps,
                llmGateway,
                new ObjectMapper(),
                postsRepository,
                systemConfigurationService
        );
    }

    private static RetrievalRagProperties baseProps(String model, int dims) {
        RetrievalRagProperties p = new RetrievalRagProperties();
        p.getEs().setEmbeddingModel(model);
        p.getEs().setEmbeddingDims(dims);
        return p;
    }
}
