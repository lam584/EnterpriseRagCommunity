package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsTestQueryResponse;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagPostTestQueryServiceTest {

    @Test
    void testQuery_shouldValidateRequiredArgs() {
        Fixture f = fixture();
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> f.service.testQuery(null, new RagPostsTestQueryRequest()));
        assertEquals("vectorIndexId is required", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> f.service.testQuery(1L, null));
        assertEquals("req is required", ex2.getMessage());

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText(" ");
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () -> f.service.testQuery(1L, req));
        assertEquals("queryText is required", ex3.getMessage());
    }

    @Test
    void testQuery_shouldReturnHits_andUpdateDims_whenStoredDimsMissing() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":0.5,"_source":{"post_id":10,"chunk_index":0,"author_id":2,"board_id":3,"title":"t","content_text":"%s"}},
                  {"_id":"d2","_source":{"title":"t2","content_text":"short"}}
                ]}}
                """.formatted("a".repeat(260)));

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("legacy-embedding-model");
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostTestQueryService svc = new RagPostTestQueryService(
                vectorIndicesRepository,
                ragProps,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName(" ");
        vi.setDim(0);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es/,mockhttp://backup");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        when(llmGateway.embedOnceRouted(
                eq(LlmQueueTaskType.POST_EMBEDDING),
                eq("pid1"),
                eq("em1"),
                eq("hello")
        )).thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        RagPostsTestQueryResponse resp = svc.testQuery(1L, req);
        assertNotNull(resp);
        assertEquals("idx_posts", resp.getIndexName());
        assertEquals(8, resp.getTopK());
        assertEquals(100, resp.getNumCandidates());
        assertEquals(3, resp.getEmbeddingDims());
        assertEquals("em1", resp.getEmbeddingModel());
        assertEquals("pid1", resp.getEmbeddingProviderId());
        assertNotNull(resp.getHits());
        assertEquals(2, resp.getHits().size());
        assertEquals("d1", resp.getHits().get(0).getDocId());
        assertTrue(resp.getHits().get(0).getContentTextPreview().endsWith("..."));

        verify(indexService, times(1)).ensureIndex("idx_posts", 3);
        ArgumentCaptor<VectorIndicesEntity> cap = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorIndicesRepository, times(1)).save(cap.capture());
        assertEquals(3, cap.getValue().getDim());
    }

    @Test
    void testQuery_shouldClampTopKAndNumCandidates_andIncludeBoardTerm() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        Fixture f = fixture();
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(f.systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f, 3f}, 3, "em1"));

        RagPostsTestQueryRequest req = request("hello", "pid1", "em1");
        req.setTopK(99);
        req.setNumCandidates(20001);
        req.setBoardId(12L);

        RagPostsTestQueryResponse resp = f.service.testQuery(1L, req);
        assertEquals(50, resp.getTopK());
        assertEquals(10_000, resp.getNumCandidates());
        assertEquals(12L, resp.getBoardId());

        MockHttpUrl.RequestCapture capture = MockHttpUrl.pollRequest();
        assertNotNull(capture);
        String body = new String(capture.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"size\":50"));
        assertTrue(body.contains("\"k\":50"));
        assertTrue(body.contains("\"num_candidates\":10000"));
        assertTrue(body.contains("\"term\":{\"board_id\":12}"));
    }

    @Test
    void testQuery_shouldReturnEmptyHits_whenEsReturnsEmptyArray() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostTestQueryService svc = new RagPostTestQueryService(
                vectorIndicesRepository,
                ragProps,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_custom");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es");

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        RagPostsTestQueryResponse resp = svc.testQuery(1L, req);
        assertNotNull(resp);
        assertNotNull(resp.getHits());
        assertTrue(resp.getHits().isEmpty());
    }

    @Test
    void testQuery_shouldRouteWithinFixedProvider_whenMetadataEnabled() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        Fixture f = fixture();
        f.vectorIndex.setMetadata(new HashMap<>() {{
            put("embeddingProviderId", " fixed-p ");
        }});
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(f.llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("fixed-p"), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("fixed-p", "fixed-m"), 1, 1, null));
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("fixed-p"), eq("fixed-m"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "fixed-m"));

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");

        RagPostsTestQueryResponse resp = f.service.testQuery(1L, req);
        assertEquals("fixed-p", resp.getEmbeddingProviderId());
        assertEquals(null, resp.getEmbeddingModel());
    }

    @Test
    void testQuery_shouldIgnoreLastBuildModel_whenFixedProviderConfigured() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        Fixture f = fixture();
        f.vectorIndex.setMetadata(new HashMap<>() {{
            put("embeddingProviderId", "fixed-p");
            put("lastBuildEmbeddingProviderId", "last-p");
            put("lastBuildEmbeddingModel", "last-m");
        }});
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(f.llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("fixed-p"), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("fixed-p", "fresh-m"), 1, 1, null));
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("fixed-p"), eq("fresh-m"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "fresh-m"));

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");

        RagPostsTestQueryResponse resp = f.service.testQuery(1L, req);
        assertEquals("fixed-p", resp.getEmbeddingProviderId());
        assertEquals(null, resp.getEmbeddingModel());
    }

    @Test
    void testQuery_shouldPickRouteTarget_whenNoOverrideAndNoFixed() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        Fixture f = fixture();
        f.vectorIndex.setMetadata(null);
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(f.llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("picked-p", "picked-m"), 1, 1, null));
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("picked-p"), eq("picked-m"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "picked-m"));

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");

        RagPostsTestQueryResponse resp = f.service.testQuery(1L, req);
        assertEquals(null, resp.getEmbeddingProviderId());
        assertEquals(null, resp.getEmbeddingModel());
    }

    @Test
    void testQuery_shouldFallbackToLegacyModel_whenPickNextReturnsNull() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        Fixture f = fixture();
        f.vectorIndex.setMetadata(new HashMap<>());
        f.ragProps.getEs().setEmbeddingModel("legacy-m");
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(f.llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any())).thenReturn(null);
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("legacy-m"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "legacy-m"));

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");

        RagPostsTestQueryResponse resp = f.service.testQuery(1L, req);
        assertEquals(null, resp.getEmbeddingProviderId());
        assertEquals(null, resp.getEmbeddingModel());
    }

    @Test
    void testQuery_shouldThrow_whenProviderOverridePresentButNoEligibleTargetAndNoLegacy() {
        Fixture f = fixture();
        f.vectorIndex.setMetadata(new HashMap<>());
        f.ragProps.getEs().setEmbeddingModel(" ");
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid-only"), any())).thenReturn(null);

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId(" pid-only ");
        req.setEmbeddingModel(" ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("Embedding failed"));
        assertTrue(ex.getMessage().contains("providerId=pid-only"));
    }

    @Test
    void testQuery_shouldThrow_whenEmbeddingReturnsNullVector() throws Exception {
        Fixture f = fixture();
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello"))).thenReturn(null);

        RagPostsTestQueryRequest req = request("hello", "pid1", "em1");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("embedding returned empty vector"));
    }

    @Test
    void testQuery_shouldThrow_whenDimsMismatch() throws Exception {
        Fixture f = fixture();
        f.vectorIndex.setDim(5);
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagPostsTestQueryRequest req = request("hello", "pid1", "em1");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("vector index dim mismatch"));
    }

    @Test
    void testQuery_shouldWrapEnsureIndexException() throws Exception {
        Fixture f = fixture();
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(f.indexService).ensureIndex("idx_custom", 3);

        RagPostsTestQueryRequest req = request("hello", "pid1", "em1");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("Ensure ES index failed: boom"));
    }

    @Test
    void testQuery_shouldUseDefaultEndpointAndSkipAuthHeader_whenConfigBlank() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":{}}}");

        Fixture f = fixture();
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn(" ");
        when(f.systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(" ");
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagPostsTestQueryRequest req = request("hello", "pid1", "em1");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.testQuery(1L, req));
        assertNotNull(ex.getMessage());
        assertTrue(!ex.getMessage().isBlank());
    }

    @Test
    void testQuery_shouldThrow_whenEsReturns500() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"bad\"}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostTestQueryService svc = new RagPostTestQueryService(
                vectorIndicesRepository,
                ragProps,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_custom");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es");

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("ES search failed"));
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("ES error HTTP 500"));
    }

    @Test
    void testQuery_shouldThrow_whenEsErrorWithoutBody() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(503, null);

        Fixture f = fixture();
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagPostsTestQueryRequest req = request("hello", "pid1", "em1");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("without body"));
    }

    @Test
    void testQuery_shouldWrapEmbeddingExceptions() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostTestQueryService svc = new RagPostTestQueryService(
                vectorIndicesRepository,
                ragProps,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_custom");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenThrow(new RuntimeException("x"));

        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().startsWith("Embedding failed: "));
    }

    @Test
    void testQuery_shouldNotSaveDims_whenStoredDimsPositive() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        Fixture f = fixture();
        f.vectorIndex.setDim(3);
        when(f.vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(f.vectorIndex));
        when(f.systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(f.llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagPostsTestQueryRequest req = request("hello", "pid1", "em1");
        f.service.testQuery(1L, req);

        verify(f.vectorIndicesRepository, never()).save(any(VectorIndicesEntity.class));
    }

    private static RagPostsTestQueryRequest request(String q, String provider, String model) {
        RagPostsTestQueryRequest req = new RagPostsTestQueryRequest();
        req.setQueryText(q);
        req.setEmbeddingProviderId(provider);
        req.setEmbeddingModel(model);
        return req;
    }

    private static Fixture fixture() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("legacy-embedding-model");
        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagPostTestQueryService svc = new RagPostTestQueryService(
                vectorIndicesRepository,
                ragProps,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_custom");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        return new Fixture(svc, vectorIndicesRepository, ragProps, indexService, llmGateway, llmRoutingService, systemConfigurationService, vi);
    }

    private record Fixture(
            RagPostTestQueryService service,
            VectorIndicesRepository vectorIndicesRepository,
            RetrievalRagProperties ragProps,
            RagPostsIndexService indexService,
            LlmGateway llmGateway,
            LlmRoutingService llmRoutingService,
            SystemConfigurationService systemConfigurationService,
            VectorIndicesEntity vectorIndex
    ) {
    }
}
