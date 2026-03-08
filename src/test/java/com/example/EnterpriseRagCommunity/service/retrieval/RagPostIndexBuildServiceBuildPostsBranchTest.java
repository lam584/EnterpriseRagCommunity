package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsBuildResponse;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagPostIndexBuildServiceBuildPostsBranchTest {

    @Test
    void buildPosts_shouldShortCircuitWhenApiKeyMissing() throws Exception {
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

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(" ");

        RagPostsBuildResponse resp = svc.buildPosts(1L, null, null, 10, 200, 0, false, "m1", "p1", 3);
        assertNotNull(resp);
        assertEquals(0, resp.getTotalPosts());

        verify(vectorIndicesRepository, never()).findById(any());
        verify(postsRepository, never()).scanByStatusAndBoardFromId(any(), any(), any(), any());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void buildPosts_shouldUseOverrideModelAndProvider_andClearIndexViaIndexOpsExistsDelete() throws Exception {
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

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("  ");
        vi.setMetric(" ");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostsEntity p = new PostsEntity();
        p.setId(10L);
        p.setBoardId(3L);
        p.setAuthorId(2L);
        p.setTitle("t");
        p.setContent("a".repeat(600));
        p.setStatus(PostStatus.PUBLISHED);
        p.setIsDeleted(false);
        p.setCreatedAt(LocalDateTime.now().minusDays(1));
        p.setUpdatedAt(LocalDateTime.now().minusHours(1));

        @SuppressWarnings("unchecked")
        Page<PostsEntity> page0 = (Page<PostsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(p));
        when(page0.hasNext()).thenReturn(false);

        when(postsRepository.scanByStatusAndBoardFromId(
                eq(PostStatus.PUBLISHED),
                eq(null),
                eq(1L),
                any()
        )).thenReturn(page0);

        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("p1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f}, 3, "m1"));

        IndexOperations ops = mock(IndexOperations.class);
        when(esTemplate.indexOps(IndexCoordinates.of("idx_posts"))).thenReturn(ops);
        when(ops.exists()).thenReturn(true);
        when(ops.delete()).thenReturn(true);

        RagPostsBuildResponse resp = svc.buildPosts(1L, null, 1L, 10, 200, 0, true, "m1", "p1", 3);

        assertNotNull(resp);
        assertEquals(1L, resp.getFromPostId());
        assertEquals(1, resp.getTotalPosts());
        assertEquals(3, resp.getEmbeddingDims());
        assertEquals("m1", resp.getEmbeddingModel());
        assertEquals("p1", resp.getEmbeddingProviderId());
        assertEquals(Boolean.TRUE, resp.getCleared());
        assertNull(resp.getClearError());
        assertTrue(resp.getTotalChunks() >= 2);
        assertEquals(resp.getTotalChunks(), resp.getSuccessChunks());
        assertEquals(0, resp.getFailedChunks());

        verify(indexService, times(1)).ensureIndex("idx_posts", 3);
        verify(esTemplate, times((int) resp.getTotalChunks())).save(any(Document.class), any(IndexCoordinates.class));

        ArgumentCaptor<VectorIndicesEntity> cap = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorIndicesRepository, times(2)).save(cap.capture());
        VectorIndicesEntity finalSaved = cap.getAllValues().get(1);
        assertEquals(VectorIndexStatus.READY, finalSaved.getStatus());
        assertEquals("cosine", finalSaved.getMetric());
        assertNotNull(finalSaved.getMetadata());
        assertEquals("idx_posts", finalSaved.getMetadata().get("esIndex"));
        assertEquals("POST", finalSaved.getMetadata().get("sourceType"));
    }

    @Test
    void buildPosts_shouldCleanupPerPost_andContinueWhenDeleteByQueryFails() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"boom\"}");

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

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1", " ");
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/,mockhttp://backup");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_posts");
        vi.setMetric(null);
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("pid1", "m1"), 1, 0, null));

        PostsEntity p = new PostsEntity();
        p.setId(10L);
        p.setBoardId(3L);
        p.setAuthorId(2L);
        p.setTitle("t");
        p.setContent("a".repeat(260));
        p.setStatus(PostStatus.PUBLISHED);
        p.setIsDeleted(false);

        @SuppressWarnings("unchecked")
        Page<PostsEntity> page0 = (Page<PostsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(p));
        when(page0.hasNext()).thenReturn(false);

        when(postsRepository.scanByStatusAndBoardFromId(eq(PostStatus.PUBLISHED), eq(null), eq(null), any()))
                .thenReturn(page0);

        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("pid1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f}, 1, "m1"));

        RagPostsBuildResponse resp = svc.buildPosts(1L, null, null, 10, 200, 0, false, null, null, 1);
        assertNotNull(resp);
        assertEquals(1, resp.getTotalPosts());
        assertEquals(1, resp.getEmbeddingDims());
        assertEquals("m1", resp.getEmbeddingModel());
        assertEquals("pid1", resp.getEmbeddingProviderId());

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertTrue(req.url().toString().contains("/idx_posts/_delete_by_query"));
    }

    @Test
    void buildPosts_shouldFallbackEnsureIndexWhenNoPosts_andClearIndexFallbackHttpDeleteOnIndexOpsError() {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"acknowledged\":true}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx with space");
        ragProps.getEs().setEmbeddingDims(3);
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
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName(" ");
        vi.setMetric(null);
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(null);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        Page<PostsEntity> page0 = (Page<PostsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(true);
        when(postsRepository.scanByStatusAndBoardFromId(eq(PostStatus.PUBLISHED), eq(null), eq(null), any()))
                .thenReturn(page0);

        when(esTemplate.indexOps(IndexCoordinates.of("idx with space"))).thenThrow(new RuntimeException("boom"));

        RagPostsBuildResponse resp = svc.buildPosts(1L, null, null, 10, 200, 0, true, "m1", "p1", 3);
        assertNotNull(resp);
        assertEquals(0, resp.getTotalPosts());
        assertEquals(0, resp.getTotalChunks());
        assertEquals(0, resp.getSuccessChunks());

        verify(indexService, times(1)).ensureIndex("idx with space", 3);
        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("DELETE", req.method());
        assertTrue(req.url().toString().contains("idx+with+space"));
    }

    @Test
    void buildPosts_shouldThrowWhenStoredDimMismatch() throws Exception {
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

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_posts");
        vi.setMetric(null);
        vi.setDim(7);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostsEntity p = new PostsEntity();
        p.setId(10L);
        p.setBoardId(3L);
        p.setAuthorId(2L);
        p.setTitle("t");
        p.setContent("a".repeat(260));
        p.setStatus(PostStatus.PUBLISHED);
        p.setIsDeleted(false);

        @SuppressWarnings("unchecked")
        Page<PostsEntity> page0 = (Page<PostsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(p));
        when(page0.hasNext()).thenReturn(false);
        when(postsRepository.scanByStatusAndBoardFromId(eq(PostStatus.PUBLISHED), eq(null), eq(null), any()))
                .thenReturn(page0);

        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("pid1", "m1"), 1, 0, null));

        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("pid1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));

        IndexOperations ops = mock(IndexOperations.class);
        when(esTemplate.indexOps(IndexCoordinates.of("idx_posts"))).thenReturn(ops);
        when(ops.exists()).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildPosts(1L, null, null, 10, 200, 0, true, null, null, 3));
        assertTrue(ex.getMessage().contains("vector index dim mismatch"));

        ArgumentCaptor<VectorIndicesEntity> cap = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorIndicesRepository, times(2)).save(cap.capture());
        assertEquals(VectorIndexStatus.ERROR, cap.getAllValues().get(1).getStatus());
    }
}
