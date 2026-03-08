package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsTestQueryResponse;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagCommentTestQueryServiceTest {

    @Test
    void testQuery_shouldReturnHits_usingDefaultIndexName() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"c1","_score":0.9,"_source":{"comment_id":1,"post_id":2,"author_id":3,"content_text":"%s"}}
                ]}}
                """.formatted("c".repeat(260)));

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel("legacy");
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentTestQueryService svc = new RagCommentTestQueryService(
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
        vi.setCollectionName("");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(indexService.defaultIndexName()).thenReturn("idx_comments");
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es");

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagCommentsTestQueryRequest req = new RagCommentsTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingModel("em1");

        RagCommentsTestQueryResponse resp = svc.testQuery(1L, req);
        assertNotNull(resp);
        assertEquals("idx_comments", resp.getIndexName());
        assertNotNull(resp.getHits());
        assertEquals(1, resp.getHits().size());
        assertTrue(resp.getHits().get(0).getContentTextPreview().endsWith("..."));
    }

    @Test
    void testQuery_shouldThrow_whenDimsMismatch() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel("legacy");
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentTestQueryService svc = new RagCommentTestQueryService(
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
        vi.setCollectionName("idx_comments");
        vi.setDim(5);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("hello")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));

        RagCommentsTestQueryRequest req = new RagCommentsTestQueryRequest();
        req.setQueryText("hello");
        req.setEmbeddingModel("em1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("vector index dim mismatch"));
    }

    @Test
    void testQuery_shouldThrow_whenNoEligibleEmbeddingTargetAndLegacyMissing() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingModel(" ");
        RagCommentsIndexService indexService = mock(RagCommentsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagCommentTestQueryService svc = new RagCommentTestQueryService(
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
        vi.setCollectionName("idx_comments");
        vi.setDim(3);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any())).thenReturn(null);

        RagCommentsTestQueryRequest req = new RagCommentsTestQueryRequest();
        req.setQueryText("hello");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.testQuery(1L, req));
        assertTrue(ex.getMessage().contains("no eligible embedding target"));
    }
}
