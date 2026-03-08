package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesBuildResponse;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagFileAssetIndexBuildServiceSyncFilesIncrementalBranchTest {

    @Test
    void syncFilesIncremental_shouldThrowWhenVectorIndexNotFound() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.empty());

        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
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
                () -> svc.syncFilesIncremental(1L, 10, 200, 0, null, null, 3));
        assertEquals("vector index not found: 1", ex.getMessage());
    }

    @Test
    void syncFilesIncremental_shouldFallbackLastToZero_andWriteNoopMetadata() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);

        RagFileAssetIndexBuildService svc = spy(new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                mock(LlmRoutingService.class),
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
        ));

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(null);

        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagFilesBuildResponse buildResp = new RagFilesBuildResponse();
        buildResp.setTotalFiles(0L);
        buildResp.setTookMs(12L);
        buildResp.setLastFileAssetId(null);
        doReturn(buildResp).when(svc).buildFiles(eq(1L), eq(null), any(), any(), any(), eq(false), any(), any(), any());

        RagFilesBuildResponse out = svc.syncFilesIncremental(1L, 10, 200, 0, null, null, 3);
        assertNotNull(out);

        verify(svc, times(1)).buildFiles(eq(1L), eq(null), eq(10), eq(200), eq(0), eq(false), eq(null), eq(null), eq(3));

        ArgumentCaptor<VectorIndicesEntity> cap = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorIndicesRepository, times(1)).save(cap.capture());
        Map<String, Object> meta = cap.getValue().getMetadata();
        assertNotNull(meta);
        assertNull(meta.get("lastSyncFromFileAssetId"));
        assertEquals(0L, meta.get("lastSyncLastFileAssetId"));
        assertEquals(0L, meta.get("lastSyncTotalFiles"));
        assertEquals(Boolean.TRUE, meta.get("lastSyncNoop"));
    }

    @Test
    void syncFilesIncremental_shouldUseLastSyncLastFileAssetId_andAdvanceNewLast() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);

        RagFileAssetIndexBuildService svc = spy(new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                mock(LlmRoutingService.class),
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
        ));

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        HashMap<String, Object> meta0 = new HashMap<>();
        meta0.put("lastSyncLastFileAssetId", " 5 ");
        meta0.put("k", "v");
        vi.setMetadata(meta0);

        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagFilesBuildResponse buildResp = new RagFilesBuildResponse();
        buildResp.setTotalFiles(2L);
        buildResp.setTookMs(34L);
        buildResp.setLastFileAssetId(7L);
        doReturn(buildResp).when(svc).buildFiles(eq(1L), eq(5L), any(), any(), any(), eq(false), any(), any(), any());

        RagFilesBuildResponse out = svc.syncFilesIncremental(1L, 10, 200, 0, null, null, 3);
        assertNotNull(out);

        verify(svc, times(1)).buildFiles(eq(1L), eq(5L), eq(10), eq(200), eq(0), eq(false), eq(null), eq(null), eq(3));

        ArgumentCaptor<VectorIndicesEntity> cap = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorIndicesRepository, times(1)).save(cap.capture());
        Map<String, Object> meta = cap.getValue().getMetadata();
        assertNotNull(meta);
        assertEquals("v", meta.get("k"));
        assertEquals(5L, meta.get("lastSyncFromFileAssetId"));
        assertEquals(7L, meta.get("lastSyncLastFileAssetId"));
        assertEquals(2L, meta.get("lastSyncTotalFiles"));
        assertEquals(Boolean.FALSE, meta.get("lastSyncNoop"));
    }

    @Test
    void syncFilesIncremental_shouldFallbackToLastBuildWhenLastSyncUnparseable() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);

        RagFileAssetIndexBuildService svc = spy(new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                mock(LlmRoutingService.class),
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
        ));

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetric("cosine");
        vi.setDim(0);
        vi.setStatus(VectorIndexStatus.READY);
        HashMap<String, Object> meta0 = new HashMap<>();
        meta0.put("lastSyncLastFileAssetId", "abc");
        meta0.put("lastBuildLastFileAssetId", 3);
        vi.setMetadata(meta0);

        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagFilesBuildResponse buildResp = new RagFilesBuildResponse();
        buildResp.setTotalFiles(0L);
        buildResp.setTookMs(1L);
        buildResp.setLastFileAssetId(null);
        doReturn(buildResp).when(svc).buildFiles(eq(1L), eq(3L), any(), any(), any(), eq(false), any(), any(), any());

        RagFilesBuildResponse out = svc.syncFilesIncremental(1L, 10, 200, 0, null, null, 3);
        assertNotNull(out);

        verify(svc, times(1)).buildFiles(eq(1L), eq(3L), eq(10), eq(200), eq(0), eq(false), eq(null), eq(null), eq(3));
    }

    @Test
    void rebuildFiles_shouldDelegateBuildAndPersistRebuildMetadata() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetIndexBuildService svc = spy(new RagFileAssetIndexBuildService(
                vectorIndicesRepository,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                mock(LlmRoutingService.class),
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                mock(SystemConfigurationService.class)
        ));

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        vi.setCollectionName("idx_files");
        vi.setMetadata(new HashMap<>());
        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(vi));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagFilesBuildResponse buildResp = new RagFilesBuildResponse();
        buildResp.setTookMs(88L);
        buildResp.setTotalFiles(9L);
        buildResp.setLastFileAssetId(77L);
        doReturn(buildResp).when(svc).buildFiles(eq(1L), eq(null), eq(20), eq(300), eq(20), eq(true), eq("m1"), eq("p1"), eq(8));

        RagFilesBuildResponse out = svc.rebuildFiles(1L, 20, 300, 20, "m1", "p1", 8);
        assertNotNull(out);
        assertEquals(88L, out.getTookMs());

        verify(svc, times(1)).buildFiles(eq(1L), eq(null), eq(20), eq(300), eq(20), eq(true), eq("m1"), eq("p1"), eq(8));

        ArgumentCaptor<VectorIndicesEntity> cap = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorIndicesRepository, times(1)).save(cap.capture());
        Map<String, Object> meta = cap.getValue().getMetadata();
        assertNotNull(meta);
        assertEquals(88L, meta.get("lastRebuildTookMs"));
        assertEquals(9L, meta.get("lastRebuildTotalFiles"));
        assertEquals(77L, meta.get("lastRebuildLastFileAssetId"));
    }
}
