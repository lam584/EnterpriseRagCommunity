package com.example.EnterpriseRagCommunity.service.retrieval.jobs;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexBuildService;
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

class RagPostAutoSyncJobTest {

    @Test
    void tick_shouldReturnWhenApiKeyBlank() {
        RagAutoSyncConfigService configService = mock(RagAutoSyncConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagPostIndexBuildService buildService = mock(RagPostIndexBuildService.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagPostAutoSyncJob job = new RagPostAutoSyncJob(configService, vectorIndicesRepository, buildService, systemConfigurationService);

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn(" ");

        job.tick();

        verify(configService, never()).getConfig();
        verify(vectorIndicesRepository, never()).findByStatus(eq(VectorIndexStatus.READY));
    }

    @Test
    void tick_shouldSkipWhenSourceTypeNotPost() {
        RagAutoSyncConfigService configService = mock(RagAutoSyncConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagPostIndexBuildService buildService = mock(RagPostIndexBuildService.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagPostAutoSyncJob job = new RagPostAutoSyncJob(configService, vectorIndicesRepository, buildService, systemConfigurationService);

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        RagAutoSyncConfigDTO cfg = new RagAutoSyncConfigDTO();
        cfg.setEnabled(true);
        cfg.setIntervalSeconds(30L);
        when(configService.getConfig()).thenReturn(cfg);

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("sourceType", "FILE_ASSET");
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findByStatus(VectorIndexStatus.READY)).thenReturn(List.of(vi));

        job.tick();

        verify(buildService, never()).syncPostsIncremental(eq(1L), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void tick_shouldCallSyncPostsIncremental_andParseMeta() {
        RagAutoSyncConfigService configService = mock(RagAutoSyncConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagPostIndexBuildService buildService = mock(RagPostIndexBuildService.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagPostAutoSyncJob job = new RagPostAutoSyncJob(configService, vectorIndicesRepository, buildService, systemConfigurationService);

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        RagAutoSyncConfigDTO cfg = new RagAutoSyncConfigDTO();
        cfg.setEnabled(true);
        cfg.setIntervalSeconds(1L);
        when(configService.getConfig()).thenReturn(cfg);

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(1L);
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("sourceType", " POST ");
        meta.put("lastSyncBoardId", " ");
        meta.put("lastBuildBoardId", " 3 ");
        meta.put("lastBuildPostBatchSize", "10");
        meta.put("lastBuildChunkMaxChars", 200);
        meta.put("lastBuildChunkOverlapChars", "0");
        meta.put("lastBuildEmbeddingDims", "3");
        vi.setMetadata(meta);
        when(vectorIndicesRepository.findByStatus(VectorIndexStatus.READY)).thenReturn(List.of(vi));

        job.tick();

        verify(buildService).syncPostsIncremental(1L, 3L, 10, 200, 0, null, null, 3);
    }

    @Test
    void tick_shouldThrottleWhenIntervalNotElapsed() throws Exception {
        RagAutoSyncConfigService configService = mock(RagAutoSyncConfigService.class);
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RagPostIndexBuildService buildService = mock(RagPostIndexBuildService.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        RagPostAutoSyncJob job = new RagPostAutoSyncJob(configService, vectorIndicesRepository, buildService, systemConfigurationService);

        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        RagAutoSyncConfigDTO cfg = new RagAutoSyncConfigDTO();
        cfg.setEnabled(true);
        cfg.setIntervalSeconds(30L);
        when(configService.getConfig()).thenReturn(cfg);

        Field f = RagPostAutoSyncJob.class.getDeclaredField("lastRunAtMs");
        f.setAccessible(true);
        AtomicLong al = (AtomicLong) f.get(job);
        al.set(System.currentTimeMillis());

        job.tick();

        verify(vectorIndicesRepository, never()).findByStatus(eq(VectorIndexStatus.READY));
    }
}
