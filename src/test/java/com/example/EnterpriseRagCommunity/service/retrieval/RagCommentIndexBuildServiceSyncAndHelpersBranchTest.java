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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagCommentIndexBuildServiceSyncAndHelpersBranchTest {

    @Test
    void rebuildAndIncremental_shouldDelegateToBuildComments() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentIndexBuildService svc = spy(new RagCommentIndexBuildService(
                vectorIndicesRepository,
                commentsRepository,
                embeddingService,
                llmRoutingService,
                ragProps,
                indexService,
                esTemplate,
                objectMapper,
                systemConfigurationService
        ));

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(9L);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("lastSyncLastCommentId", "5");
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findById(9L)).thenReturn(Optional.of(vi));

        RagCommentsBuildResponse mocked = new RagCommentsBuildResponse();
        doReturn(mocked).when(svc).buildComments(9L, null, 10, 700, 30, true, "m1", 3);
        doReturn(mocked).when(svc).buildComments(9L, 5L, 12, 701, 31, false, "m2", 4);

        RagCommentsBuildResponse out1 = svc.rebuildComments(9L, 10, 700, 30, "m1", 3);
        RagCommentsBuildResponse out2 = svc.syncCommentsIncremental(9L, 12, 701, 31, "m2", 4);

        assertNotNull(out1);
        assertNotNull(out2);
        verify(svc, times(1)).buildComments(9L, null, 10, 700, 30, true, "m1", 3);
        verify(svc, times(1)).buildComments(9L, 5L, 12, 701, 31, false, "m2", 4);
    }

    @Test
    void syncSingleComment_shouldCoverCoreBranches() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"deleted\":1}");
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");
        MockHttpUrl.enqueue(200, "{\"aggregations\":{\"max_floor\":{\"value\":\"4\"}}}");
        MockHttpUrl.enqueue(200, "{\"deleted\":1}");

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

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es, mockhttp://es2");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_single");
        vi.setDim(null);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        CommentsEntity c = new CommentsEntity();
        c.setId(10L);
        c.setPostId(2L);
        c.setParentId(null);
        c.setAuthorId(3L);
        c.setStatus(CommentStatus.VISIBLE);
        c.setIsDeleted(false);
        c.setContent("single comment content");
        c.setCreatedAt(LocalDateTime.now().minusMinutes(3));
        c.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        when(commentsRepository.findById(10L)).thenReturn(Optional.of(c));

        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p1", "m1"), 1, 1, null))
                .thenReturn(null);

        when(embeddingService.embedOnceForTask(eq("single comment content"), eq("m1"), eq("p1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "m1"));

        IndexOperations ops = mock(IndexOperations.class);
        when(esTemplate.indexOps(any(IndexCoordinates.class))).thenReturn(ops);
        doThrow(new RuntimeException("refresh fail")).when(ops).refresh();

        assertThrows(IllegalArgumentException.class, () -> svc.syncSingleComment(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> svc.syncSingleComment(1L, null));

        svc.syncSingleComment(1L, 10L);
        verify(indexService, times(1)).ensureIndex("idx_single", 3);

        CommentsEntity hidden = new CommentsEntity();
        hidden.setId(11L);
        hidden.setPostId(2L);
        hidden.setStatus(CommentStatus.HIDDEN);
        hidden.setIsDeleted(false);
        hidden.setContent("hidden");
        when(commentsRepository.findById(11L)).thenReturn(Optional.of(hidden));
        svc.syncSingleComment(1L, 11L);
        verify(embeddingService, times(1)).embedOnceForTask(any(), any(), any(), any());

        CommentsEntity c2 = new CommentsEntity();
        c2.setId(12L);
        c2.setPostId(3L);
        c2.setStatus(CommentStatus.VISIBLE);
        c2.setIsDeleted(false);
        c2.setContent("need-route");
        when(commentsRepository.findById(12L)).thenReturn(Optional.of(c2));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.syncSingleComment(1L, 12L));
        assertTrue(ex.getMessage().contains("no eligible embedding target"));
    }

    @Test
    void helperMethods_shouldCoverHttpParseAndPrivateBranches() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[{\"_source\":{\"comment_floor\":6}}]}}");
        MockHttpUrl.enqueue(200, "{\"aggregations\":{\"max_floor\":{\"value\":7.0}}}");
        MockHttpUrl.enqueue(200, "{\"aggregations\":{\"max_floor\":{\"value\":\"8\"}}}");
        MockHttpUrl.enqueue(200, "{\"aggregations\":{\"max_floor\":{\"value\":\"abc\"}}}");
        MockHttpUrl.enqueue(200, "{\"ok\":true}");
        MockHttpUrl.enqueue(500, "{\"error\":\"bad\"}");
        MockHttpUrl.enqueue(404, "{\"error\":\"missing\"}");
        MockHttpUrl.enqueue(500, "{\"error\":\"bad-index\"}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
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

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k2");

        Integer floor = (Integer) invokePrivate(svc, "findExistingCommentFloor", new Class[]{String.class, Long.class}, "idx", 1L);
        assertEquals(6, floor);
        Integer max1 = (Integer) invokePrivate(svc, "findExistingPostMaxFloor", new Class[]{String.class, Long.class}, "idx", 1L);
        Integer max2 = (Integer) invokePrivate(svc, "findExistingPostMaxFloor", new Class[]{String.class, Long.class}, "idx", 2L);
        Integer max3 = (Integer) invokePrivate(svc, "findExistingPostMaxFloor", new Class[]{String.class, Long.class}, "idx", 3L);
        assertEquals(7, max1);
        assertEquals(8, max2);
        assertNull(max3);

        assertNull(invokePrivate(svc, "findExistingCommentFloor", new Class[]{String.class, Long.class}, " ", 1L));
        assertNull(invokePrivate(svc, "findExistingCommentFloor", new Class[]{String.class, Long.class}, "idx", null));
        assertNull(invokePrivate(svc, "findExistingPostMaxFloor", new Class[]{String.class, Long.class}, " ", 1L));
        assertNull(invokePrivate(svc, "findExistingPostMaxFloor", new Class[]{String.class, Long.class}, "idx", null));

        JsonNode node = (JsonNode) invokePrivate(
                svc,
                "postSearch",
                new Class[]{String.class, String.class, String.class},
                "idx",
                "{\"query\":{\"match_all\":{}}}",
                "hits.hits._source.comment_floor"
        );
        assertNotNull(node);

        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"deleted\":1}");
        assertDoesNotThrow(() -> invokePrivate(svc, "deleteByQuery", new Class[]{String.class, String.class}, "idx", null));
        MockHttpUrl.enqueue(500, "{\"error\":\"bad\"}");
        IllegalStateException ex1 = assertThrows(IllegalStateException.class,
                () -> invokePrivate(svc, "deleteByQuery", new Class[]{String.class, String.class}, "idx", "{\"query\":{}}"));
        assertTrue(ex1.getMessage().contains("delete_by_query"));

        MockHttpUrl.enqueue(404, "{\"error\":\"missing\"}");
        assertDoesNotThrow(() -> invokePrivate(svc, "deleteIndexViaHttp", new Class[]{String.class}, "idx"));
        MockHttpUrl.enqueue(500, "{\"error\":\"bad-index\"}");
        IllegalStateException ex2 = assertThrows(IllegalStateException.class,
                () -> invokePrivate(svc, "deleteIndexViaHttp", new Class[]{String.class}, "idx"));
        assertTrue(ex2.getMessage().contains("delete index"));

        assertThrows(IllegalArgumentException.class,
                () -> invokePrivate(svc, "deleteByQuery", new Class[]{String.class, String.class}, " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> invokePrivate(svc, "deleteIndexViaHttp", new Class[]{String.class}, " "));
    }

    @Test
    void utilityMethods_shouldCoverStaticAndTouchMetadataBranches() throws Exception {
        assertNull(invokeStatic("toLong", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(12L, invokeStatic("toLong", new Class[]{Object.class}, new Object[]{12}));
        assertEquals(15L, invokeStatic("toLong", new Class[]{Object.class}, new Object[]{" 15 "}));
        assertNull(invokeStatic("toLong", new Class[]{Object.class}, new Object[]{"x"}));

        assertEquals(0, invokeStatic("toInt", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(7, invokeStatic("toInt", new Class[]{Object.class}, new Object[]{7}));
        assertEquals(9, invokeStatic("toInt", new Class[]{Object.class}, new Object[]{" 9 "}));

        assertNull(invokeStatic("toIntBoxed", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(6, invokeStatic("toIntBoxed", new Class[]{Object.class}, new Object[]{6}));
        assertEquals(8, invokeStatic("toIntBoxed", new Class[]{Object.class}, new Object[]{" 8 "}));
        assertNull(invokeStatic("toIntBoxed", new Class[]{Object.class}, new Object[]{"x"}));

        assertNull(invokeStatic("toNonBlankString", new Class[]{Object.class}, new Object[]{null}));
        assertNull(invokeStatic("toNonBlankString", new Class[]{Object.class}, new Object[]{"   "}));
        assertEquals("abc", invokeStatic("toNonBlankString", new Class[]{Object.class}, new Object[]{" abc "}));

        assertDoesNotThrow(() -> invokeStatic("validateEmbeddingDims", new Class[]{Integer.class, Integer.class}, new Object[]{3, 3}));
        IllegalStateException dimEx = assertThrows(IllegalStateException.class,
                () -> invokeStatic("validateEmbeddingDims", new Class[]{Integer.class, Integer.class}, new Object[]{3, 4}));
        assertTrue(dimEx.getMessage().contains("embedding dims mismatch"));

        assertNull(invokeStatic("summarizeException", new Class[]{Throwable.class}, new Object[]{null}));
        assertEquals("IllegalStateException",
                invokeStatic("summarizeException", new Class[]{Throwable.class}, new Object[]{new IllegalStateException()}));
        String longMsg = "x".repeat(2000);
        String summarized = (String) invokeStatic("summarizeException", new Class[]{Throwable.class}, new Object[]{new IllegalStateException(longMsg)});
        assertTrue(summarized.length() <= 803);
        assertTrue(summarized.endsWith("..."));

        @SuppressWarnings("unchecked")
        List<String> a = (List<String>) invokeStatic("splitWithOverlap", new Class[]{String.class, int.class, int.class}, new Object[]{null, 10, 0});
        assertTrue(a.isEmpty());
        @SuppressWarnings("unchecked")
        List<String> b = (List<String>) invokeStatic("splitWithOverlap", new Class[]{String.class, int.class, int.class}, new Object[]{"hello", 10, 0});
        assertEquals(List.of("hello"), b);
        @SuppressWarnings("unchecked")
        List<String> c = (List<String>) invokeStatic("splitWithOverlap", new Class[]{String.class, int.class, int.class}, new Object[]{"x".repeat(30), 10, 3});
        assertTrue(c.size() > 1);

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
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

        assertDoesNotThrow(() -> invokePrivate(svc, "touchMetadata", new Class[]{Long.class, java.util.function.Consumer.class}, null, null));
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> invokePrivate(svc, "touchMetadata", new Class[]{Long.class, java.util.function.Consumer.class}, 1L, null));

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(2L);
        vi.setMetadata(null);
        when(vectorIndicesRepository.findById(2L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        assertDoesNotThrow(() -> invokePrivate(svc, "touchMetadata", new Class[]{Long.class, java.util.function.Consumer.class}, 2L, (java.util.function.Consumer<Map<String, Object>>) m -> m.put("k", "v")));
        assertNotNull(vi.getMetadata());
        assertEquals("v", vi.getMetadata().get("k"));
        assertFalse(vi.getMetadata().isEmpty());
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

    private static Object invokeStatic(String name, Class<?>[] types, Object[] args) throws Exception {
        Method m = RagCommentIndexBuildService.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception ex) throw ex;
            if (c instanceof Error err) throw err;
            throw e;
        }
    }
}
