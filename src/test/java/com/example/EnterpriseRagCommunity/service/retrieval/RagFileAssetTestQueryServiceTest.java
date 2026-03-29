package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesTestQueryResponse;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;

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

class RagFileAssetTestQueryServiceTest {

    @Test
    void testQuery_shouldValidateRequiredArgs_andVectorIndexNotFound() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> svc.testQuery(null, new RagFilesTestQueryRequest()));
        assertEquals("vectorIndexId is required", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> svc.testQuery(1L, null));
        assertEquals("req is required", ex2.getMessage());

        RagFilesTestQueryRequest req1 = new RagFilesTestQueryRequest();
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () -> svc.testQuery(1L, req1));
        assertEquals("queryText is required", ex3.getMessage());

        RagFilesTestQueryRequest req2 = new RagFilesTestQueryRequest();
        req2.setQueryText(" ");
        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class, () -> svc.testQuery(1L, req2));
        assertEquals("queryText is required", ex4.getMessage());

        RagFilesTestQueryRequest req3 = new RagFilesTestQueryRequest();
        req3.setQueryText("hello");
        when(vectorIndicesRepository.findById(9L)).thenReturn(Optional.empty());
        IllegalArgumentException ex5 = assertThrows(IllegalArgumentException.class, () -> svc.testQuery(9L, req3));
        assertTrue(ex5.getMessage().contains("vector index not found: 9"));
    }

    @Test
    void testQuery_shouldReturnHits_andParsePostIds() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"f1","_score":0.7,"_source":{
                    "file_asset_id":11,
                    "chunk_index":1,
                    "owner_user_id":2,
                    "file_name":"a.pdf",
                    "mime_type":"application/pdf",
                    "post_ids":[1,2,"x"],
                    "content_text":"%s"
                  }}
                ]}}
                """.formatted("b".repeat(260)));

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName(" ");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(indexService.defaultIndexName()).thenReturn("idx_files");
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es");

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");
        req.setPostId(2L);

        RagFilesTestQueryResponse resp = svc.testQuery(1L, req);
        assertNotNull(resp);
        assertEquals("idx_files", resp.getIndexName());
        assertNotNull(resp.getHits());
        assertEquals(1, resp.getHits().size());
        assertEquals("f1", resp.getHits().get(0).getDocId());
        assertEquals(2, resp.getHits().get(0).getPostIds().size());
        assertTrue(resp.getHits().get(0).getContentTextPreview().endsWith("..."));

        verify(indexService).ensureIndex("idx_files", 3, true);
    }

    @Test
    void testQuery_shouldRouteWithinFixedProviderAndSaveDims_whenOverrideIncomplete() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName(" idx_fixed ");
        vi.setMetadata(new HashMap<>() {{
            put("embeddingProviderId", "fp");
        }});
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("fp"), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("fp", "fm"), 1, 1, null));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es-fixed/,mockhttp://es-backup");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY"))
                .thenReturn("  key-1  ");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("fp"), eq("fm"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.2f, 0.3f, 0.4f}, 3, "fm"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setTopK(0);
        req.setNumCandidates(1);
        req.setEmbeddingProviderId("  fp ");

        RagFilesTestQueryResponse resp = svc.testQuery(1L, req);
        assertNotNull(resp);
        assertEquals(1, resp.getTopK());
        assertEquals(10, resp.getNumCandidates());
        assertEquals("fp", resp.getEmbeddingProviderId());
        assertEquals(null, resp.getEmbeddingModel());
        verify(vectorIndicesRepository).save(vi);

        MockHttpUrl.RequestCapture capture = MockHttpUrl.pollRequest();
        assertNotNull(capture);
        assertEquals("ApiKey key-1", capture.headers().get("Authorization"));
        assertEquals("mockhttp://es-fixed/idx_fixed/_search?filter_path=hits.hits._id,hits.hits._score,hits.hits._source", capture.url().toString());
    }

    @Test
    void testQuery_shouldIgnoreLastBuildModel_whenFixedProviderConfigured() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(2);
        vi.setMetadata(new HashMap<>() {{
            put("embeddingProviderId", "fp");
            put("lastBuildEmbeddingModel", "lm");
            put("lastBuildEmbeddingProviderId", "lp");
        }});
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es");
        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("fp"), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("fp", "fresh-m"), 1, 1, null));
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("fp"), eq("fresh-m"), eq("q2")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.2f, 0.3f}, 2, "fresh-m"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("q2");

        RagFilesTestQueryResponse resp = svc.testQuery(1L, req);
        assertNotNull(resp);
        assertEquals(null, resp.getEmbeddingModel());
        assertEquals("fp", resp.getEmbeddingProviderId());
    }

    @Test
    void testQuery_shouldClampUpperBounds_andIncludeFileAndPostFilters() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f, 3f}, 3, "em1"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");
        req.setTopK(99);
        req.setNumCandidates(20001);
        req.setFileAssetId(12L);
        req.setPostId(34L);

        RagFilesTestQueryResponse resp = svc.testQuery(1L, req);
        assertEquals(50, resp.getTopK());
        assertEquals(10_000, resp.getNumCandidates());

        MockHttpUrl.RequestCapture capture = MockHttpUrl.pollRequest();
        assertNotNull(capture);
        String body = new String(capture.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"size\":50"));
        assertTrue(body.contains("\"k\":50"));
        assertTrue(body.contains("\"num_candidates\":10000"));
        assertTrue(body.contains("\"term\":{\"file_asset_id\":12}"));
        assertTrue(body.contains("\"term\":{\"post_ids\":34}"));
    }

    @Test
    void testQuery_shouldUsePickNextInProvider_whenOnlyProviderOverride() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid2"), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("pid2", "em2"), 1, 1, null));
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid2"), eq("em2"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f, 3f}, 3, "em2"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid2");

        RagFilesTestQueryResponse resp = svc.testQuery(1L, req);
        assertEquals("pid2", resp.getEmbeddingProviderId());
        assertEquals(null, resp.getEmbeddingModel());
    }

    @Test
    void testQuery_shouldThrow_whenNoEligibleTargetWithProviderConstraint() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid-x"), any())).thenReturn(null);

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid-x");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("no eligible embedding target for providerId=pid-x"));
    }

    @Test
    void testQuery_shouldThrow_whenEmbeddingThrows() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("pid", "em"), 1, 1, null));
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid"), eq("em"), eq("hello")))
                .thenThrow(new RuntimeException("bad"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("Embedding failed: bad"));
    }

    @Test
    void testQuery_shouldThrow_whenEmbeddingVectorEmpty() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("pid", "em"), 1, 1, null));
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid"), eq("em"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{}, 0, "em"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("embedding returned empty vector"));
    }

    @Test
    void testQuery_shouldThrow_whenEmbeddingVectorNull() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("pid", "em"), 1, 1, null));
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid"), eq("em"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(null, 0, "em"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("embedding returned empty vector"));
    }

    @Test
    void testQuery_shouldThrow_whenDimMismatch() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(4);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("pid", "em"), 1, 1, null));
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid"), eq("em"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("vector index dim mismatch"));
    }

    @Test
    void testQuery_shouldThrow_whenEnsureIndexFails() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("pid", "em"), 1, 1, null));
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid"), eq("em"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em"));
        when(indexService.defaultIndexName()).thenReturn("idx_files");
        doThrow(new RuntimeException("es down")).when(indexService).ensureIndex("idx_files", 3, true);

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("Ensure ES index failed: es down"));
    }

    @Test
    void testQuery_shouldThrow_whenEsReturnsErrorBody() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"x\"}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("ES error HTTP 500"));
    }

    @Test
    void testQuery_shouldThrow_whenEsReturnsErrorWithoutBody() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, null);

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("without body"));
    }

    @Test
    void testQuery_shouldThrow_whenEsReturnsInvalidJson() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("ES search failed"));
    }

    @Test
    void testQuery_shouldThrow_whenElasticsearchUrisBlankFallsBackToDefaultEndpoint() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn(" ");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("ES search failed"));
    }

    @Test
    void testQuery_shouldHandleHitWithoutNumericPostIds() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"f2","_source":{"post_ids":["x","y"],"content_text":"short"}}
                ]}}
                """);

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq("pid1"), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingProviderId("pid1");
        req.setEmbeddingModel("em1");

        RagFilesTestQueryResponse resp = svc.testQuery(1L, req);
        assertNotNull(resp);
        assertEquals(1, resp.getHits().size());
        assertEquals("f2", resp.getHits().get(0).getDocId());
        assertEquals(null, resp.getHits().get(0).getPostIds());
        assertEquals("short", resp.getHits().get(0).getContentTextPreview());
    }

    @Test
    void helperMethods_shouldCoverToNonBlankAndBuildBodyBranches() throws Exception {
        Method toNonBlank = RagFileAssetTestQueryService.class.getDeclaredMethod("toNonBlank", Object.class);
        toNonBlank.setAccessible(true);
        assertEquals(null, toNonBlank.invoke(null, new Object[]{null}));
        assertEquals(null, toNonBlank.invoke(null, "   "));
        assertEquals("x", toNonBlank.invoke(null, " x "));

        Method build = RagFileAssetTestQueryService.class.getDeclaredMethod(
                "buildKnnSearchBody",
                int.class,
                int.class,
                Long.class,
                Long.class,
                float[].class
        );
        build.setAccessible(true);

        String bodyNone = (String) build.invoke(null, 2, 100, null, null, new float[]{1f, 2f});
        assertTrue(bodyNone.contains("\"filter\":[]"));

        String bodyFileOnly = (String) build.invoke(null, 2, 100, 11L, null, new float[]{1f});
        assertTrue(bodyFileOnly.contains("\"term\":{\"file_asset_id\":11}"));
        assertTrue(!bodyFileOnly.contains("\"term\":{\"post_ids\":"));

        String bodyPostOnly = (String) build.invoke(null, 2, 100, null, 22L, new float[]{1f});
        assertTrue(bodyPostOnly.contains("\"term\":{\"post_ids\":22}"));

        String bodyBoth = (String) build.invoke(null, 2, 100, 11L, 22L, new float[]{1f});
        assertTrue(bodyBoth.contains("\"term\":{\"file_asset_id\":11}"));
        assertTrue(bodyBoth.contains(",{\"term\":{\"post_ids\":22}}"));
    }

    @Test
    void testQuery_shouldThrow_whenNoEligibleTarget() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetTestQueryService svc = new RagFileAssetTestQueryService(
                vectorIndicesRepository,
                indexService,
                llmGateway,
                llmRoutingService,
                objectMapper,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any())).thenReturn(null);

        RagFilesTestQueryRequest req = new RagFilesTestQueryRequest();
        req.setQueryText("hello");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("no eligible embedding target"));
        verifyNoInteractions(llmGateway);
    }
}
