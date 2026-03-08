package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsBuildResponse;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagCommentIndexBuildServiceBuildCommentsBranchTest {

    @Test
    void buildComments_shouldProcessChunks_andCollectFailures_andTouchMetadata() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel(" ");
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentIndexBuildService svc = new RagCommentIndexBuildService(
                vectorIndicesRepository,
                commentsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName(" ");
        vi.setDim(null);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(indexService.defaultIndexName()).thenReturn("idx_comments");
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("provider_a", "emb_model_a"), 1, 1, null));

        CommentsEntity parent = comment(100L, 9L, null, 8L, "parent", CommentStatus.VISIBLE, false);
        when(commentsRepository.findById(100L)).thenReturn(Optional.of(parent));

        CommentsEntity c1 = comment(1L, 9L, null, 11L, "hello comment one", CommentStatus.VISIBLE, false);
        CommentsEntity c2 = comment(2L, 9L, 100L, 12L, "hello comment two", CommentStatus.VISIBLE, false);
        CommentsEntity c3 = comment(3L, 9L, null, 12L, "deleted", CommentStatus.VISIBLE, true);
        CommentsEntity c4 = comment(4L, 9L, null, 12L, "hidden", CommentStatus.HIDDEN, false);
        CommentsEntity c5 = comment(5L, 9L, null, 12L, "   ", CommentStatus.VISIBLE, false);

        Page<CommentsEntity> page = new PageImpl<>(
                Arrays.asList(null, c1, c2, c3, c4, c5),
                PageRequest.of(0, 2),
                6
        );
        when(commentsRepository.scanVisibleFromId(any(), any())).thenReturn(page, Page.empty());

        when(embeddingService.embedOnceForTask(eq("hello comment one"), eq("emb_model_a"), eq("provider_a"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "emb_model_a"));
        when(embeddingService.embedOnceForTask(eq("hello comment two"), eq("emb_model_a"), eq("provider_a"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{}, 0, "emb_model_a"));

        IndexOperations ops = mock(IndexOperations.class);
        when(esTemplate.indexOps(any(IndexCoordinates.class))).thenReturn(ops);
        when(ops.exists()).thenReturn(true);
        doThrow(new RuntimeException("refresh failed")).when(ops).refresh();

        RagCommentsBuildResponse resp = svc.buildComments(1L, 10L, 2, 199, -1, true, null, 3);

        assertNotNull(resp);
        assertEquals(3L, resp.getTotalComments());
        assertEquals(2L, resp.getTotalChunks());
        assertEquals(1L, resp.getSuccessChunks());
        assertEquals(1L, resp.getFailedChunks());
        assertEquals(5L, resp.getLastCommentId());
        assertEquals("emb_model_a", resp.getEmbeddingModel());
        assertEquals(3, resp.getEmbeddingDims());
        assertEquals(800, resp.getChunkMaxChars());
        assertEquals(80, resp.getChunkOverlapChars());
        assertEquals(Boolean.TRUE, resp.getCleared());
        assertNull(resp.getClearError());
        assertEquals(1, resp.getFailedDocs().size());
        assertTrue(resp.getFailedDocs().get(0).getError().contains("embedding returned empty vector"));

        verify(indexService, times(1)).ensureIndex("idx_comments", 3);
        verify(ops, times(1)).delete();
        verify(ops, times(1)).refresh();

        Map<String, Object> meta = vi.getMetadata();
        assertNotNull(meta);
        assertEquals("COMMENT", meta.get("sourceType"));
        assertEquals("idx_comments", meta.get("esIndex"));
        assertEquals(3L, ((Number) meta.get("lastBuildTotalComments")).longValue());
        assertEquals(2L, ((Number) meta.get("lastBuildTotalChunks")).longValue());
        assertEquals(1L, ((Number) meta.get("lastBuildSuccessChunks")).longValue());
        assertEquals(1L, ((Number) meta.get("lastBuildFailedChunks")).longValue());
        assertEquals(2, ((Number) meta.get("lastBuildCommentBatchSize")).intValue());
        assertEquals(10L, ((Number) meta.get("lastBuildFromCommentId")).longValue());
        assertEquals(5L, ((Number) meta.get("lastBuildLastCommentId")).longValue());
        assertEquals("emb_model_a", meta.get("lastBuildEmbeddingModel"));
        assertEquals(Boolean.TRUE, meta.get("lastBuildCleared"));
        assertEquals(5L, ((Number) meta.get("lastSyncLastCommentId")).longValue());
    }

    @Test
    void buildComments_shouldFallbackEnsureIndexWhenNoData() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingDims(8);
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentIndexBuildService svc = new RagCommentIndexBuildService(
                vectorIndicesRepository,
                commentsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_comments_2");
        vi.setDim(null);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentsRepository.scanVisibleFromId(any(), any())).thenReturn(Page.empty());

        IndexOperations ops = mock(IndexOperations.class);
        when(esTemplate.indexOps(any(IndexCoordinates.class))).thenReturn(ops);

        RagCommentsBuildResponse resp = svc.buildComments(1L, null, null, null, null, false, "emb_fix", null);

        assertNotNull(resp);
        assertEquals(0L, resp.getTotalComments());
        assertEquals(0L, resp.getTotalChunks());
        assertEquals(0L, resp.getSuccessChunks());
        assertEquals(0L, resp.getFailedChunks());
        assertEquals("emb_fix", resp.getEmbeddingModel());
        assertNull(resp.getCleared());
        assertNull(resp.getClearError());
        verify(indexService, times(1)).ensureIndex("idx_comments_2", 8);
        verify(ops, times(1)).refresh();
        verify(embeddingService, never()).embedOnceForTask(any(), any(), any(), any());
    }

    @Test
    void buildComments_shouldCaptureClearErrorWhenDeleteFailed() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"boom\"}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingDims(0);
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentIndexBuildService svc = new RagCommentIndexBuildService(
                vectorIndicesRepository,
                commentsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_comments_clear_fail");
        vi.setDim(null);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("");

        CommentsEntity c1 = comment(10L, 8L, null, 1L, "clear-fail-case", CommentStatus.VISIBLE, false);
        when(commentsRepository.scanVisibleFromId(any(), any())).thenReturn(new PageImpl<>(List.of(c1)));
        when(embeddingService.embedOnceForTask(eq("clear-fail-case"), eq("emb_m"), eq(null), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f}, 1, "emb_m"));

        IndexOperations ops = mock(IndexOperations.class);
        when(esTemplate.indexOps(any(IndexCoordinates.class))).thenReturn(ops);
        when(ops.exists()).thenReturn(true);
        doThrow(new RuntimeException("delete failed")).when(ops).delete();

        RagCommentsBuildResponse resp = svc.buildComments(1L, null, 10, 800, 0, true, "emb_m", null);

        assertNotNull(resp);
        assertEquals(1L, resp.getTotalComments());
        assertEquals(1L, resp.getTotalChunks());
        assertEquals(0L, resp.getSuccessChunks());
        assertEquals(1L, resp.getFailedChunks());
        assertEquals(Boolean.FALSE, resp.getCleared());
        assertNotNull(resp.getClearError());
        assertFalse(resp.getClearError().isBlank());
        assertEquals(1, resp.getFailedDocIds().size());
        verify(indexService, never()).ensureIndex(any(), anyInt());

        Method m = RagCommentIndexBuildService.class.getDeclaredMethod("summarizeException", Throwable.class);
        m.setAccessible(true);
        String summarized = (String) m.invoke(null, new IllegalStateException("x".repeat(2000)));
        assertNotNull(summarized);
        assertTrue(summarized.endsWith("..."));
    }

    private static CommentsEntity comment(Long id,
                                          Long postId,
                                          Long parentId,
                                          Long authorId,
                                          String content,
                                          CommentStatus status,
                                          boolean deleted) {
        CommentsEntity c = new CommentsEntity();
        c.setId(id);
        c.setPostId(postId);
        c.setParentId(parentId);
        c.setAuthorId(authorId);
        c.setContent(content);
        c.setStatus(status);
        c.setIsDeleted(deleted);
        c.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        c.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        return c;
    }
}
