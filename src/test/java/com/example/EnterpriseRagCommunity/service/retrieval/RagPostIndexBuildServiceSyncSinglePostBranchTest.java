package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagPostIndexBuildServiceSyncSinglePostBranchTest {

    @Test
    void syncSinglePost_shouldReturnWhenIdsNull() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostIndexBuildService svc = new RagPostIndexBuildService(
                vectorIndicesRepository,
                postsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        svc.syncSinglePost(null, 1L, null, null, null, null, null);
        svc.syncSinglePost(1L, null, null, null, null, null, null);

        verifyNoInteractions(systemConfigurationService);
        verifyNoInteractions(vectorIndicesRepository);
        verifyNoInteractions(postsRepository);
        verifyNoInteractions(embeddingService);
    }

    @Test
    void syncSinglePost_shouldSkipWhenApiKeyMissing() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostIndexBuildService svc = new RagPostIndexBuildService(
                vectorIndicesRepository,
                postsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("");

        svc.syncSinglePost(1L, 10L, null, null, null, null, null);

        verify(vectorIndicesRepository, never()).findById(any());
        verify(postsRepository, never()).findById(any());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void syncSinglePost_shouldReturnWhenPostInvalid() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex(" ");
        ragProps.getEs().setEmbeddingModel("legacy-emb");
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostIndexBuildService svc = new RagPostIndexBuildService(
                vectorIndicesRepository,
                postsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName(" ");
        vi.setMetric(null);
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        PostsEntity p = new PostsEntity();
        p.setId(10L);
        p.setBoardId(3L);
        p.setAuthorId(2L);
        p.setTitle("t");
        p.setContent("c");
        p.setStatus(PostStatus.DRAFT);
        p.setIsDeleted(false);
        when(postsRepository.findById(10L)).thenReturn(Optional.of(p));

        svc.syncSinglePost(1L, 10L, null, null, null, null, null);

        verifyNoInteractions(embeddingService);
        verify(indexService, never()).ensureIndex(any(), anyInt());
    }

    @Test
    void syncSinglePost_shouldUseFixedEnabledTargetFromMetadata_andSaveDocs_andIgnoreRefreshFailure() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"deleted\":1}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("legacy-emb");
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostIndexBuildService svc = new RagPostIndexBuildService(
                vectorIndicesRepository,
                postsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1", "k1");
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/,mockhttp://backup");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_posts");
        vi.setMetric(null);
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("embeddingModel", "m_fixed");
        meta.put("embeddingProviderId", "p_fixed");
        meta.put("lastBuildChunkMaxChars", "100");
        meta.put("lastBuildChunkOverlapChars", "99999");
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        when(llmRoutingService.isEnabledTarget(eq(LlmQueueTaskType.POST_EMBEDDING), eq("p_fixed"), eq("m_fixed")))
                .thenReturn(true);

        PostsEntity p = new PostsEntity();
        p.setId(10L);
        p.setBoardId(3L);
        p.setAuthorId(2L);
        p.setTitle("t");
        p.setContent("a".repeat(80));
        p.setStatus(PostStatus.PUBLISHED);
        p.setIsDeleted(false);
        p.setCreatedAt(LocalDateTime.now().minusDays(1));
        p.setUpdatedAt(LocalDateTime.now().minusHours(1));
        when(postsRepository.findById(10L)).thenReturn(Optional.of(p));

        when(embeddingService.embedOnceForTask(any(), eq("m_fixed"), eq("p_fixed"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{}, 3, "m_fixed"));

        IndexOperations ops = mock(IndexOperations.class);
        when(esTemplate.indexOps(any(IndexCoordinates.class))).thenReturn(ops);
        doThrow(new RuntimeException("boom")).when(ops).refresh();

        svc.syncSinglePost(1L, 10L, null, null, null, null, 3);

        verify(indexService, times(1)).ensureIndex("idx_posts", 3);
        long saveCalls = org.mockito.Mockito.mockingDetails(esTemplate)
                .getInvocations()
                .stream()
                .filter(inv -> "save".equals(inv.getMethod().getName()))
                .count();
        assertTrue(saveCalls >= 1);
        verify(ops, times(1)).refresh();

        MockHttpUrl.RequestCapture delReq = MockHttpUrl.pollRequest();
        assertNotNull(delReq);
        assertEquals("POST", delReq.method());
        assertTrue(delReq.url().toString().contains("/idx_posts/_delete_by_query"));
    }

    @Test
    void syncSinglePost_shouldThrowWhenNoRoutingAndNoLegacy() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex(" ");
        ragProps.getEs().setEmbeddingModel(" ");
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostIndexBuildService svc = new RagPostIndexBuildService(
                vectorIndicesRepository,
                postsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName(" ");
        vi.setMetric(null);
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        PostsEntity p = new PostsEntity();
        p.setId(10L);
        p.setBoardId(3L);
        p.setAuthorId(2L);
        p.setTitle("t");
        p.setContent("c");
        p.setStatus(PostStatus.PUBLISHED);
        p.setIsDeleted(false);
        when(postsRepository.findById(10L)).thenReturn(Optional.of(p));

        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any())).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.syncSinglePost(1L, 10L, 200, 0, null, null, 3));
        assertTrue(ex.getMessage().contains("no eligible embedding target"));
    }

    @Test
    void httpDeleteHelpers_shouldHandleDefaults_headers_andStatusBranches() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"ok\":true}");
        MockHttpUrl.enqueue(500, "{\"error\":\"bad\"}");
        MockHttpUrl.enqueue(404, "{\"error\":\"missing\"}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostIndexBuildService svc = new RagPostIndexBuildService(
                vectorIndicesRepository,
                postsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/,mockhttp://backup");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        assertDoesNotThrow(() -> invokePrivate(svc, "deleteByQuery", new Class[]{String.class, String.class}, "idx", null));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokePrivate(svc, "deleteByQuery", new Class[]{String.class, String.class}, "idx", "{\"query\":{}}"));
        assertTrue(ex.getMessage().contains("delete_by_query"));

        assertDoesNotThrow(() -> invokePrivate(svc, "deleteIndexViaHttp", new Class[]{String.class}, "idx"));
        assertDoesNotThrow(() -> invokePrivate(svc, "deleteIndexViaHttp", new Class[]{String.class}, "idx"));

        MockHttpUrl.RequestCapture r1 = MockHttpUrl.pollRequest();
        MockHttpUrl.RequestCapture r2 = MockHttpUrl.pollRequest();
        MockHttpUrl.RequestCapture r3 = MockHttpUrl.pollRequest();
        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);
        assertEquals("POST", r1.method());
        assertNotNull(r1.headers().get("Content-Type"));
        assertTrue(new String(r1.body()).contains("match_all"));
    }

    private static Object invokePrivate(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception ex) throw ex;
            if (c instanceof Error err) throw err;
            throw e;
        }
    }
}
