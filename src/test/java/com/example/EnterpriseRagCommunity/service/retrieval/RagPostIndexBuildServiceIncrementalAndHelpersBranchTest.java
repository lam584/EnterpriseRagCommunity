package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsBuildResponse;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagPostIndexBuildServiceIncrementalAndHelpersBranchTest {

    @Test
    void syncPostsIncremental_shouldUseLastSyncAndUpdateMetadata_noopTrue() {
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

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setStatus(VectorIndexStatus.READY);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("lastSyncLastPostId", "5");
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagPostsBuildResponse resp = svc.syncPostsIncremental(1L, 2L, null, null, null, null, null, null);
        assertNotNull(resp);
        assertEquals(0, resp.getTotalPosts());

        verify(vectorIndicesRepository, times(1)).save(any(VectorIndicesEntity.class));
        Map<String, Object> outMeta = vi.getMetadata();
        assertEquals(6L, outMeta.get("lastSyncFromPostId"));
        assertEquals(5L, outMeta.get("lastSyncLastPostId"));
        assertEquals(Boolean.TRUE, outMeta.get("lastSyncNoop"));
    }

    @Test
    void syncPostsIncremental_shouldFallbackToLastBuildAndDefaultToZero() {
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

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setStatus(VectorIndexStatus.READY);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("lastSyncLastPostId", "  ");
        meta.put("lastBuildLastPostId", 7L);
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagPostsBuildResponse resp = svc.syncPostsIncremental(1L, null, null, null, null, null, null, null);
        assertNotNull(resp);

        Map<String, Object> outMeta = vi.getMetadata();
        assertEquals(8L, outMeta.get("lastSyncFromPostId"));
        assertEquals(7L, outMeta.get("lastSyncLastPostId"));

        vi.getMetadata().clear();
        RagPostsBuildResponse resp2 = svc.syncPostsIncremental(1L, null, null, null, null, null, null, null);
        assertNotNull(resp2);
        Map<String, Object> outMeta2 = vi.getMetadata();
        assertNull(outMeta2.get("lastSyncFromPostId"));
        assertEquals(0L, outMeta2.get("lastSyncLastPostId"));
    }

    @Test
    void rebuildPosts_shouldTouchMetadataEvenWhenBuildSkipped() {
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

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(null);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagPostsBuildResponse resp = svc.rebuildPosts(1L, 2L, null, null, null, null, null, null);
        assertNotNull(resp);

        Map<String, Object> outMeta = vi.getMetadata();
        assertNotNull(outMeta);
        assertNotNull(outMeta.get("lastRebuildAt"));
        assertEquals(2L, outMeta.get("lastRebuildBoardId"));
    }

    @Test
    void helperMethods_shouldCoverParseAndSummarizeBranches() throws Exception {
        assertNull(invokeStatic("toLong", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(123L, invokeStatic("toLong", new Class[]{Object.class}, new Object[]{123}));
        assertEquals(5L, invokeStatic("toLong", new Class[]{Object.class}, new Object[]{" 5 "}));
        assertNull(invokeStatic("toLong", new Class[]{Object.class}, new Object[]{"x"}));

        assertNull(invokeStatic("toInt", new Class[]{Object.class}, new Object[]{null}));
        assertEquals(7, invokeStatic("toInt", new Class[]{Object.class}, new Object[]{7}));
        assertEquals(9, invokeStatic("toInt", new Class[]{Object.class}, new Object[]{" 9 "}));
        assertNull(invokeStatic("toInt", new Class[]{Object.class}, new Object[]{"x"}));

        assertNull(invokeStatic("toNonBlankString", new Class[]{Object.class}, new Object[]{null}));
        assertNull(invokeStatic("toNonBlankString", new Class[]{Object.class}, new Object[]{"   "}));
        assertEquals("a", invokeStatic("toNonBlankString", new Class[]{Object.class}, new Object[]{" a "}));

        @SuppressWarnings("unchecked")
        List<Float> empty = (List<Float>) invokeStatic("toFloatList", new Class[]{float[].class}, new Object[]{new float[0]});
        assertEquals(List.of(), empty);
        @SuppressWarnings("unchecked")
        List<Float> floats = (List<Float>) invokeStatic("toFloatList", new Class[]{float[].class}, new Object[]{new float[]{0.1f, 0.2f}});
        assertEquals(List.of(0.1f, 0.2f), floats);

        assertNull(invokeStatic("summarizeException", new Class[]{Throwable.class}, new Object[]{null}));
        assertEquals("IllegalStateException", invokeStatic("summarizeException", new Class[]{Throwable.class}, new Object[]{new IllegalStateException()}));
        String longMsg = "x".repeat(2000);
        String out = (String) invokeStatic("summarizeException", new Class[]{Throwable.class}, new Object[]{new IllegalStateException(longMsg)});
        assertTrue(out.length() <= 803);
        assertTrue(out.endsWith("..."));
    }

    @Test
    void splitWithOverlap_shouldHandleNullBlankShortLongAndBlankParts() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> a = (List<String>) invokeStatic("splitWithOverlap", new Class[]{String.class, int.class, int.class}, new Object[]{null, 10, 0});
        assertEquals(0, a.size());

        @SuppressWarnings("unchecked")
        List<String> b = (List<String>) invokeStatic("splitWithOverlap", new Class[]{String.class, int.class, int.class}, new Object[]{"   ", 10, 0});
        assertEquals(0, b.size());

        @SuppressWarnings("unchecked")
        List<String> c = (List<String>) invokeStatic("splitWithOverlap", new Class[]{String.class, int.class, int.class}, new Object[]{"hello", 10, 0});
        assertEquals(List.of("hello"), c);

        String spaced = "a" + " ".repeat(250) + "b";
        @SuppressWarnings("unchecked")
        List<String> d = (List<String>) invokeStatic("splitWithOverlap", new Class[]{String.class, int.class, int.class}, new Object[]{spaced, 100, 0});
        assertTrue(d.size() >= 2);
        assertEquals("a", d.get(0));

        String s = "x".repeat(30);
        @SuppressWarnings("unchecked")
        List<String> e = (List<String>) invokeStatic("splitWithOverlap", new Class[]{String.class, int.class, int.class}, new Object[]{s, 10, 3});
        assertTrue(e.size() > 1);
    }

    @Test
    void buildPostChunkDocument_shouldPopulateFieldsAndSkipEmptyEmbedding() throws Exception {
        com.example.EnterpriseRagCommunity.entity.content.PostsEntity post = new com.example.EnterpriseRagCommunity.entity.content.PostsEntity();
        post.setId(9L);
        post.setBoardId(7L);
        post.setAuthorId(5L);
        post.setTitle("title");
        post.setCreatedAt(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        post.setUpdatedAt(LocalDateTime.of(2024, 1, 2, 4, 5, 6));

        Document withEmbedding = (Document) invokeStatic(
                "buildPostChunkDocument",
                new Class[]{com.example.EnterpriseRagCommunity.entity.content.PostsEntity.class, String.class, int.class, String.class, String.class, float[].class},
                new Object[]{post, "doc1", 2, "hash1", "chunk1", new float[]{0.3f}}
        );
        assertEquals("doc1", withEmbedding.getId());
        assertEquals("doc1", withEmbedding.get("id"));
        assertEquals(9L, withEmbedding.get("post_id"));
        assertEquals(2, withEmbedding.get("chunk_index"));
        assertEquals("hash1", withEmbedding.get("content_hash"));
        assertEquals("chunk1", withEmbedding.get("content_text"));
        assertNotNull(withEmbedding.get("created_at"));
        assertNotNull(withEmbedding.get("updated_at"));
        assertEquals(List.of(0.3f), withEmbedding.get("embedding"));

        Document withoutEmbedding = (Document) invokeStatic(
                "buildPostChunkDocument",
                new Class[]{com.example.EnterpriseRagCommunity.entity.content.PostsEntity.class, String.class, int.class, String.class, String.class, float[].class},
                new Object[]{post, "doc2", 3, "hash2", "chunk2", new float[0]}
        );
        assertNull(withoutEmbedding.get("embedding"));
    }

    @Test
    void touchMetadata_shouldHandleNulls() throws Exception {
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

        assertDoesNotThrow(() -> invokePrivate(svc, "touchMetadata", new Class[]{Long.class, java.util.function.Consumer.class}, new Object[]{null, null}));
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> invokePrivate(svc, "touchMetadata", new Class[]{Long.class, java.util.function.Consumer.class}, new Object[]{1L, null}));
    }

    private static Object invokePrivate(Object target, String name, Class<?>[] types, Object[] args) throws Exception {
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
        Method m = RagPostIndexBuildService.class.getDeclaredMethod(name, types);
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
