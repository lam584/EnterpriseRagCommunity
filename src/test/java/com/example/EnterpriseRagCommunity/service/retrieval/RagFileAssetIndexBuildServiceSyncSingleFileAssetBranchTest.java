package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagFileAssetIndexBuildServiceSyncSingleFileAssetBranchTest {

    @Test
    void syncSingleFileAsset_shouldReturnWhenIdsNull() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                postAttachmentsRepository,
                embeddingService,
                llmRoutingService,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        svc.syncSingleFileAsset(null, 1L, null, null, null);
        svc.syncSingleFileAsset(1L, null, null, null, null);

        verifyNoInteractions(vectorIndicesRepository);
        verifyNoInteractions(fileAssetsRepository);
        verifyNoInteractions(fileAssetExtractionsRepository);
        verifyNoInteractions(postAttachmentsRepository);
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(llmRoutingService);
        verifyNoInteractions(indexService);
        verifyNoInteractions(esTemplate);
        verifyNoInteractions(systemConfigurationService);
    }

    @Test
    void syncSingleFileAsset_shouldReturnWhenFileOrExtractionInvalid() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                postAttachmentsRepository,
                embeddingService,
                llmRoutingService,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        when(fileAssetsRepository.findById(10L)).thenReturn(Optional.empty());

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(10L);
        ex.setExtractStatus(FileAssetExtractionStatus.READY);
        ex.setExtractedText("a");
        when(fileAssetExtractionsRepository.findById(10L)).thenReturn(Optional.of(ex));

        svc.syncSingleFileAsset(1L, 10L, null, null, null);

        verifyNoInteractions(embeddingService);
        verify(indexService, never()).ensureIndex(anyString(), anyInt(), anyBoolean());
        verify(esTemplate, never()).save(any(), any(IndexCoordinates.class));
    }

    @Test
    void syncSingleFileAsset_shouldUseFixedEnabledTargetFromMetadata_andSaveDocs_andIgnoreDeleteByQueryFailure() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"boom\"}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                postAttachmentsRepository,
                embeddingService,
                llmRoutingService,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(" ");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric(null);
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("embeddingProviderId", "p_fixed");
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("p_fixed"), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p_fixed", "m_fixed"), 1, 1, null));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(10L);
        fa.setOriginalName("f.txt");
        fa.setMimeType("text/plain");
        when(fileAssetsRepository.findById(10L)).thenReturn(Optional.of(fa));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(10L);
        ex.setExtractStatus(FileAssetExtractionStatus.READY);
        ex.setExtractedText("a".repeat(260));
        when(fileAssetExtractionsRepository.findById(10L)).thenReturn(Optional.of(ex));

        PostAttachmentsEntity pa1 = new PostAttachmentsEntity();
        pa1.setFileAssetId(10L);
        pa1.setPostId(99L);
        PostAttachmentsEntity pa2 = new PostAttachmentsEntity();
        pa2.setFileAssetId(10L);
        pa2.setPostId(99L);
        when(postAttachmentsRepository.findByFileAssetIdIn(eq(List.of(10L)))).thenReturn(List.of(pa1, pa2));

        when(embeddingService.embedOnceForTask(anyString(), eq("m_fixed"), eq("p_fixed"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f}, 3, "m_fixed"));

        assertDoesNotThrow(() -> svc.syncSingleFileAsset(1L, 10L, 200, 0, 3));

        verify(indexService, atLeastOnce()).ensureIndex("idx_files", 3, true);
        verify(esTemplate, atLeastOnce()).save(any(Document.class), any(IndexCoordinates.class));
        assertNotNull(MockHttpUrl.pollRequest());
    }

    @Test
    void syncSingleFileAsset_shouldThrowWhenNoEligibleEmbeddingTarget() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                postAttachmentsRepository,
                embeddingService,
                llmRoutingService,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any())).thenReturn(null);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(10L);
        fa.setOriginalName("f.txt");
        fa.setMimeType("text/plain");
        when(fileAssetsRepository.findById(10L)).thenReturn(Optional.of(fa));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(10L);
        ex.setExtractStatus(FileAssetExtractionStatus.READY);
        ex.setExtractedText("a".repeat(260));
        when(fileAssetExtractionsRepository.findById(10L)).thenReturn(Optional.of(ex));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> svc.syncSingleFileAsset(1L, 10L, 200, 0, 3));
        assertTrue(e.getMessage().contains("no eligible embedding target"));
    }

    @Test
    void syncSingleFileAsset_shouldWrapEmbeddingExceptionAsIllegalState() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                postAttachmentsRepository,
                embeddingService,
                llmRoutingService,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p1", "m1"), 1, 0, null));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(10L);
        fa.setOriginalName("f.txt");
        fa.setMimeType("text/plain");
        when(fileAssetsRepository.findById(10L)).thenReturn(Optional.of(fa));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(10L);
        ex.setExtractStatus(FileAssetExtractionStatus.READY);
        ex.setExtractedText("a".repeat(260));
        when(fileAssetExtractionsRepository.findById(10L)).thenReturn(Optional.of(ex));

        doThrow(new RuntimeException("boom")).when(embeddingService)
                .embedOnceForTask(anyString(), anyString(), anyString(), eq(LlmQueueTaskType.POST_EMBEDDING));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> svc.syncSingleFileAsset(1L, 10L, 200, 0, 3));
        assertTrue(e.getMessage().startsWith("Embedding failed:"));
    }

    @Test
    void syncSingleFileAsset_shouldThrowWhenVectorIndexNotFound() {
        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
                mock(VectorIndicesRepository.class),
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                mock(LlmRoutingService.class),
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.syncSingleFileAsset(1L, 10L, 200, 0, 3));
        assertEquals("vector index not found: 1", ex.getMessage());
    }

    @Test
    void syncSingleFileAsset_shouldUseLastBuildTargetAndDefaultIndexName() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"deleted\":1}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                postAttachmentsRepository,
                embeddingService,
                llmRoutingService,
                indexService,
                esTemplate,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(" ");
        when(indexService.defaultIndexName()).thenReturn("idx_default");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName(" ");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));

        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p_pick", "m_pick"), 1, 1, null));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(10L);
        fa.setOriginalName(" ");
        fa.setMimeType(" ");
        when(fileAssetsRepository.findById(10L)).thenReturn(Optional.of(fa));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(10L);
        ex.setExtractStatus(FileAssetExtractionStatus.READY);
        ex.setExtractedText("abc".repeat(100));
        when(fileAssetExtractionsRepository.findById(10L)).thenReturn(Optional.of(ex));
        when(postAttachmentsRepository.findByFileAssetIdIn(eq(List.of(10L)))).thenReturn(null);

        when(embeddingService.embedOnceForTask(anyString(), eq("m_pick"), eq("p_pick"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[0], 3, "m_pick"));

        assertDoesNotThrow(() -> svc.syncSingleFileAsset(1L, 10L, 200, 0, null));
        verify(indexService, atLeastOnce()).ensureIndex("idx_default", 3, true);
        verify(esTemplate, atLeastOnce()).save(any(Document.class), any(IndexCoordinates.class));
        assertNotNull(MockHttpUrl.pollRequest());
    }
}
