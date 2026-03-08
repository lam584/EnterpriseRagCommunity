package com.example.EnterpriseRagCommunity.service.retrieval.jobs;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.RagAutoSyncConfigService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagFileAssetAutoSyncJobTest {

    @Test
    void tick_shouldReturnWhenDisabled() {
        RagAutoSyncConfigService configService = mock(RagAutoSyncConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetIndexBuildService buildService = mock(RagFileAssetIndexBuildService.class);
        RagFileAssetAutoSyncJob job = new RagFileAssetAutoSyncJob(configService, vectorIndicesRepository, buildService);

        when(configService.getConfig()).thenReturn(null);
        job.tick();
        verify(vectorIndicesRepository, never()).findByStatus(eq(VectorIndexStatus.READY));
        verify(buildService, never()).syncFilesIncremental(eq(1L), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));

        RagAutoSyncConfigDTO cfg = new RagAutoSyncConfigDTO();
        cfg.setEnabled(false);
        when(configService.getConfig()).thenReturn(cfg);
        job.tick();
        verify(vectorIndicesRepository, never()).findByStatus(eq(VectorIndexStatus.READY));
    }

    @Test
    void tick_shouldCallSyncForFileAssetSourceType_andParseInts() {
        RagAutoSyncConfigService configService = mock(RagAutoSyncConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetIndexBuildService buildService = mock(RagFileAssetIndexBuildService.class);
        RagFileAssetAutoSyncJob job = new RagFileAssetAutoSyncJob(configService, vectorIndicesRepository, buildService);

        RagAutoSyncConfigDTO cfg = new RagAutoSyncConfigDTO();
        cfg.setEnabled(true);
        cfg.setIntervalSeconds(1L);
        when(configService.getConfig()).thenReturn(cfg);

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("sourceType", "FILE_ASSET");
        meta.put("lastBuildFileBatchSize", "10");
        meta.put("lastBuildChunkMaxChars", 200);
        meta.put("lastBuildChunkOverlapChars", "0");
        meta.put("lastBuildEmbeddingDims", "3");
        vi.setMetadata(meta);

        when(vectorIndicesRepository.findByStatus(VectorIndexStatus.READY)).thenReturn(List.of(vi));

        job.tick();

        verify(buildService).syncFilesIncremental(1L, 10, 200, 0, null, null, 3);
    }

    @Test
    void tick_shouldThrottleWhenIntervalNotElapsed() throws Exception {
        RagAutoSyncConfigService configService = mock(RagAutoSyncConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagFileAssetIndexBuildService buildService = mock(RagFileAssetIndexBuildService.class);
        RagFileAssetAutoSyncJob job = new RagFileAssetAutoSyncJob(configService, vectorIndicesRepository, buildService);

        RagAutoSyncConfigDTO cfg = new RagAutoSyncConfigDTO();
        cfg.setEnabled(true);
        cfg.setIntervalSeconds(30L);
        when(configService.getConfig()).thenReturn(cfg);

        Field f = RagFileAssetAutoSyncJob.class.getDeclaredField("lastRunAtMs");
        f.setAccessible(true);
        AtomicLong al = (AtomicLong) f.get(job);
        al.set(System.currentTimeMillis());

        job.tick();

        verify(vectorIndicesRepository, never()).findByStatus(eq(VectorIndexStatus.READY));
    }
}
