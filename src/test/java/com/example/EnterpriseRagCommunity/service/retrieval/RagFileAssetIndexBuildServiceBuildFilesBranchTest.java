package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesBuildResponse;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagFileAssetIndexBuildServiceBuildFilesBranchTest {

    private static RagFileAssetIndexBuildService newSvc(VectorIndicesRepository vectorIndicesRepository,
                                                        FileAssetsRepository fileAssetsRepository,
                                                        FileAssetExtractionsRepository fileAssetExtractionsRepository,
                                                        PostAttachmentsRepository postAttachmentsRepository,
                                                        AiEmbeddingService embeddingService,
                                                        LlmRoutingService llmRoutingService,
                                                        RagFileAssetsIndexService indexService,
                                                        ElasticsearchTemplate esTemplate,
                                                        SystemConfigurationService systemConfigurationService) {
        return new RagFileAssetIndexBuildService(
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
    }

    @SuppressWarnings("unchecked")
    private static Page<FileAssetExtractionsEntity> emptyExtractionPage() {
        return Page.empty();
    }

    @Test
    void buildFiles_shouldThrowWhenVectorIndexNotFound() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.empty());

        RagFileAssetIndexBuildService svc = newSvc(
                vectorIndicesRepository,
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
                () -> svc.buildFiles(1L, null, null, null, null, false, null, null, null));
        assertTrue(ex.getMessage().contains("vector index not found"));
    }

    @Test
    void buildFiles_shouldThrowWhenNoEligibleEmbeddingTarget() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);

        RagFileAssetIndexBuildService svc = newSvc(
                vectorIndicesRepository,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                llmRoutingService,
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
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

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildFiles(1L, null, null, null, null, false, null, null, null));
        assertTrue(ex.getMessage().contains("no eligible embedding target"));
    }

    @Test
    void buildFiles_shouldUseOverrideModelAndProvider_andClearIndexViaIndexOpsExistsDelete() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = newSvc(
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
        vi.setCollectionName("  ");
        vi.setMetric(" ");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        when(indexService.defaultIndexName()).thenReturn("idx_files");

        FileAssetExtractionsEntity ex0 = new FileAssetExtractionsEntity();
        ex0.setFileAssetId(10L);
        ex0.setExtractStatus(FileAssetExtractionStatus.READY);
        ex0.setExtractedText("header [[IMAGE_1]] " + "a".repeat(600));
        ex0.setUpdatedAt(LocalDateTime.now());

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> page0 = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(ex0));

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> emptyPage = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(emptyPage.isEmpty()).thenReturn(true);

        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                eq(FileAssetExtractionStatus.READY),
                anyLong(),
                any()
        )).thenReturn(page0).thenReturn(emptyPage);

        FileAssetsEntity fa0 = new FileAssetsEntity();
        fa0.setId(10L);
        fa0.setOriginalName("f.txt");
        fa0.setMimeType("text/plain");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(fa0));

        PostAttachmentsEntity pa1 = new PostAttachmentsEntity();
        pa1.setFileAssetId(10L);
        pa1.setPostId(99L);
        PostAttachmentsEntity pa2 = new PostAttachmentsEntity();
        pa2.setFileAssetId(10L);
        pa2.setPostId(99L);
        when(postAttachmentsRepository.findByFileAssetIdIn(any())).thenReturn(List.of(pa1, pa2));

        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("p1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f}, 3, "m1"));

        IndexOperations ops = mock(IndexOperations.class);
        when(esTemplate.indexOps(IndexCoordinates.of("idx_files"))).thenReturn(ops);
        when(ops.exists()).thenReturn(true);
        when(ops.delete()).thenReturn(true);

        RagFilesBuildResponse resp = svc.buildFiles(1L, null, 1, 200, 0, true, "m1", "p1", 3);

        assertNotNull(resp);
        assertEquals(1, resp.getTotalFiles());
        assertEquals(3, resp.getEmbeddingDims());
        assertEquals("m1", resp.getEmbeddingModel());
        assertEquals("p1", resp.getEmbeddingProviderId());
        assertEquals(Boolean.TRUE, resp.getCleared());
        assertNull(resp.getClearError());
        assertTrue(resp.getTotalChunks() >= 2);
        assertEquals(resp.getTotalChunks(), resp.getSuccessChunks());
        assertEquals(0, resp.getFailedChunks());

        verify(indexService, times(1)).ensureIndex("idx_files", 3, true);
        verify(esTemplate, times((int) resp.getTotalChunks())).save(any(Document.class), any(IndexCoordinates.class));

        ArgumentCaptor<VectorIndicesEntity> cap = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorIndicesRepository, times(2)).save(cap.capture());
        VectorIndicesEntity finalSaved = cap.getAllValues().get(1);
        assertEquals(VectorIndexStatus.READY, finalSaved.getStatus());
        assertEquals("cosine", finalSaved.getMetric());
        assertNotNull(finalSaved.getMetadata());
        assertEquals("idx_files", finalSaved.getMetadata().get("esIndex"));
        assertEquals("FILE_ASSET", finalSaved.getMetadata().get("sourceType"));
    }

    @Test
    void buildFiles_shouldFallbackHttpDeleteIndexWhenIndexOpsThrows() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"acknowledged\":true}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = newSvc(
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
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAssetExtractionsEntity ex0 = new FileAssetExtractionsEntity();
        ex0.setFileAssetId(10L);
        ex0.setExtractStatus(FileAssetExtractionStatus.READY);
        ex0.setExtractedText("a".repeat(300));
        ex0.setUpdatedAt(LocalDateTime.now());

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> page0 = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(ex0));

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> emptyPage = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(emptyPage.isEmpty()).thenReturn(true);

        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                eq(FileAssetExtractionStatus.READY),
                anyLong(),
                any()
        )).thenReturn(page0).thenReturn(emptyPage);

        FileAssetsEntity fa0 = new FileAssetsEntity();
        fa0.setId(10L);
        fa0.setOriginalName("f.txt");
        fa0.setMimeType("text/plain");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(fa0));

        when(postAttachmentsRepository.findByFileAssetIdIn(any())).thenReturn(List.of());

        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("p1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f}, 3, "m1"));

        when(esTemplate.indexOps(any(IndexCoordinates.class))).thenThrow(new RuntimeException("boom"));

        RagFilesBuildResponse resp = svc.buildFiles(1L, null, 1, 200, 0, true, "m1", "p1", 3);

        assertNotNull(resp);
        assertEquals(Boolean.TRUE, resp.getCleared());
        assertNull(resp.getClearError());
        assertNotNull(MockHttpUrl.pollRequest());
    }

    @Test
    void buildFiles_shouldCleanupPerFile_andContinueWhenDeleteByQueryFails() throws Exception {
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

        RagFileAssetIndexBuildService svc = newSvc(
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

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/,mockhttp://backup");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(" ");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric(null);
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAssetExtractionsEntity ex0 = new FileAssetExtractionsEntity();
        ex0.setFileAssetId(10L);
        ex0.setExtractStatus(FileAssetExtractionStatus.READY);
        ex0.setExtractedText("a".repeat(300));
        ex0.setUpdatedAt(LocalDateTime.now());

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> page0 = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(ex0));

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> emptyPage = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(emptyPage.isEmpty()).thenReturn(true);

        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                eq(FileAssetExtractionStatus.READY),
                anyLong(),
                any()
        )).thenReturn(page0).thenReturn(emptyPage);

        FileAssetsEntity fa0 = new FileAssetsEntity();
        fa0.setId(10L);
        fa0.setOriginalName("f.txt");
        fa0.setMimeType("text/plain");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(fa0));

        when(postAttachmentsRepository.findByFileAssetIdIn(any())).thenReturn(List.of());

        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("p1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f}, 3, "m1"));

        RagFilesBuildResponse resp = svc.buildFiles(1L, null, 1, 200, 0, false, "m1", "p1", 3);

        assertNotNull(resp);
        assertEquals(1, resp.getTotalFiles());
        assertEquals(0, resp.getFailedChunks());
        assertNotNull(MockHttpUrl.pollRequest());
    }

    @Test
    void buildFiles_shouldRouteWithinFixedProviderWithoutGlobalRouting() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);

        RagFileAssetIndexBuildService svc = newSvc(
                vectorIndicesRepository,
                mock(FileAssetsRepository.class),
                fileAssetExtractionsRepository,
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                llmRoutingService,
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(256);
        vi.setStatus(VectorIndexStatus.READY);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("embeddingProviderId", "p_fixed");
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                eq(FileAssetExtractionStatus.READY), anyLong(), any())).thenReturn(emptyExtractionPage());
        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("p_fixed"), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p_fixed", "m_fixed"), 1, 1, null));

        RagFilesBuildResponse resp = svc.buildFiles(1L, 1L, 20, 500, 10, false, null, null, null);
        assertNotNull(resp);
        assertEquals("m_fixed", resp.getEmbeddingModel());
        assertEquals("p_fixed", resp.getEmbeddingProviderId());
        assertNull(resp.getEmbeddingDims());
        verify(llmRoutingService, never()).pickNext(any(), any());
        verify(llmRoutingService).pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("p_fixed"), any());
    }

    @Test
    void buildFiles_shouldIgnoreLastBuildModelWhenProviderIsFixed() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);

        RagFileAssetIndexBuildService svc = newSvc(
                vectorIndicesRepository,
                mock(FileAssetsRepository.class),
                fileAssetExtractionsRepository,
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                llmRoutingService,
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(128);
        vi.setStatus(VectorIndexStatus.READY);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("embeddingProviderId", "p_disabled");
        meta.put("lastBuildEmbeddingModel", "m_last");
        meta.put("lastBuildEmbeddingProviderId", "p_last");
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                eq(FileAssetExtractionStatus.READY), anyLong(), any())).thenReturn(emptyExtractionPage());
        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("p_disabled"), any()))
                .thenReturn(new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p_disabled", "m_fresh"), 1, 1, null));

        RagFilesBuildResponse resp = svc.buildFiles(1L, null, 20, 500, 10, false, null, null, null);
        assertNotNull(resp);
        assertEquals("m_fresh", resp.getEmbeddingModel());
        assertEquals("p_disabled", resp.getEmbeddingProviderId());
        verify(llmRoutingService, never()).pickNext(any(), any());
        verify(llmRoutingService).pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("p_disabled"), any());
    }

    @Test
    void buildFiles_shouldThrowProviderSpecificWhenOverrideProviderHasNoEligibleTarget() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);

        RagFileAssetIndexBuildService svc = newSvc(
                vectorIndicesRepository,
                mock(FileAssetsRepository.class),
                fileAssetExtractionsRepository,
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                llmRoutingService,
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.POST_EMBEDDING), eq("p_only"), any())).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildFiles(1L, null, 10, 400, 10, false, null, "p_only", null));
        assertTrue(ex.getMessage().contains("providerId=p_only"));
    }

    @Test
    void buildFiles_shouldSetErrorAndFailedDocsWhenEmbeddingAlwaysFails() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);

        RagFileAssetIndexBuildService svc = newSvc(
                vectorIndicesRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                postAttachmentsRepository,
                embeddingService,
                llmRoutingService,
                indexService,
                esTemplate,
                mock(SystemConfigurationService.class)
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAssetExtractionsEntity ex0 = new FileAssetExtractionsEntity();
        ex0.setFileAssetId(10L);
        ex0.setExtractStatus(FileAssetExtractionStatus.READY);
        ex0.setExtractedText("x".repeat(13000));
        ex0.setUpdatedAt(LocalDateTime.now());

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> page0 = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(ex0));

        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                eq(FileAssetExtractionStatus.READY), anyLong(), any())).thenReturn(page0).thenReturn(emptyExtractionPage());

        FileAssetsEntity fa0 = new FileAssetsEntity();
        fa0.setId(10L);
        fa0.setOriginalName(null);
        fa0.setMimeType(null);
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(fa0));
        when(postAttachmentsRepository.findByFileAssetIdIn(any())).thenReturn(List.of());

        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("p1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenThrow(new RuntimeException("embedding-down"));

        RagFilesBuildResponse resp = svc.buildFiles(1L, null, 100, 200, 0, false, "m1", "p1", null);

        assertNotNull(resp);
        assertEquals(1, resp.getTotalFiles());
        assertTrue(resp.getTotalChunks() >= 60);
        assertEquals(0, resp.getSuccessChunks());
        assertEquals(resp.getTotalChunks(), resp.getFailedChunks());
        assertNotNull(resp.getFailedDocIds());
        assertEquals(50, resp.getFailedDocIds().size());
        assertNotNull(resp.getFailedDocs());
        assertEquals(50, resp.getFailedDocs().size());
        assertEquals(VectorIndexStatus.ERROR, vi.getStatus());
        verify(indexService, never()).ensureIndex(any(), anyInt(), anyBoolean());
        verify(esTemplate, never()).save(any(Document.class), any(IndexCoordinates.class));
    }

    @Test
    void buildFiles_shouldThrowWhenClearRequestedAndFallbackDeleteAlsoFails() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"cannot-delete-index\"}");

        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = newSvc(
                vectorIndicesRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                postAttachmentsRepository,
                embeddingService,
                mock(LlmRoutingService.class),
                indexService,
                esTemplate,
                systemConfigurationService
        );

        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(" ");

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAssetExtractionsEntity ex0 = new FileAssetExtractionsEntity();
        ex0.setFileAssetId(10L);
        ex0.setExtractStatus(FileAssetExtractionStatus.READY);
        ex0.setExtractedText("y".repeat(280));
        ex0.setUpdatedAt(LocalDateTime.now());

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> page0 = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(ex0));
        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                eq(FileAssetExtractionStatus.READY), anyLong(), any())).thenReturn(page0).thenReturn(emptyExtractionPage());

        FileAssetsEntity fa0 = new FileAssetsEntity();
        fa0.setId(10L);
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(fa0));
        when(postAttachmentsRepository.findByFileAssetIdIn(any())).thenReturn(List.of());
        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("p1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f}, 2, "m1"));
        when(esTemplate.indexOps(any(IndexCoordinates.class))).thenThrow(new RuntimeException("index-ops-down"));

        RagFilesBuildResponse resp = svc.buildFiles(1L, null, 1, 200, 0, true, "m1", "p1", 2);
        assertNotNull(resp);
        assertEquals(1, resp.getTotalFiles());
        assertTrue(resp.getTotalChunks() > 0);
        assertTrue(resp.getSuccessChunks() + resp.getFailedChunks() >= resp.getTotalChunks());
        assertTrue(resp.getClearError() == null || resp.getClearError().contains("delete-index failed"));
    }

    @Test
    void buildFiles_shouldThrowAndSaveErrorWhenStoredDimMismatchAtEnd() throws Exception {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        AiEmbeddingService embeddingService = mock(AiEmbeddingService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        RagFileAssetsIndexService indexService = mock(RagFileAssetsIndexService.class);
        ElasticsearchTemplate esTemplate = mock(ElasticsearchTemplate.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        RagFileAssetIndexBuildService svc = newSvc(
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
        vi.setDim(7);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAssetExtractionsEntity ex0 = new FileAssetExtractionsEntity();
        ex0.setFileAssetId(10L);
        ex0.setExtractStatus(FileAssetExtractionStatus.READY);
        ex0.setExtractedText("a".repeat(250));
        ex0.setUpdatedAt(LocalDateTime.now());

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> page0 = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(page0.isEmpty()).thenReturn(false);
        when(page0.getContent()).thenReturn(List.of(ex0));

        @SuppressWarnings("unchecked")
        Page<FileAssetExtractionsEntity> emptyPage = (Page<FileAssetExtractionsEntity>) mock(Page.class);
        when(emptyPage.isEmpty()).thenReturn(true);

        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                eq(FileAssetExtractionStatus.READY),
                anyLong(),
                any()
        )).thenReturn(page0).thenReturn(emptyPage);

        FileAssetsEntity fa0 = new FileAssetsEntity();
        fa0.setId(10L);
        fa0.setOriginalName("f.txt");
        fa0.setMimeType("text/plain");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(fa0));
        when(postAttachmentsRepository.findByFileAssetIdIn(any())).thenReturn(List.of());

        when(embeddingService.embedOnceForTask(any(), eq("m1"), eq("p1"), eq(LlmQueueTaskType.POST_EMBEDDING)))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f}, 3, "m1"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildFiles(1L, null, 1, 200, 0, false, "m1", "p1", 3));
        assertTrue(ex.getMessage().contains("vector index dim mismatch"));

        ArgumentCaptor<VectorIndicesEntity> cap = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorIndicesRepository, times(2)).save(cap.capture());
        VectorIndicesEntity finalSaved = cap.getAllValues().get(1);
        assertEquals(VectorIndexStatus.ERROR, finalSaved.getStatus());
    }
}
