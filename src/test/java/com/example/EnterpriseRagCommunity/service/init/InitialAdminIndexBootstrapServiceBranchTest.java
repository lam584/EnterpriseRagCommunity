package com.example.EnterpriseRagCommunity.service.init;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesIndexConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSamplesIndexConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesSyncService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexBuildService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialAdminIndexBootstrapServiceBranchTest {

    @Test
    void bootstrap_shouldSeedModerationConfigAndSkipRagRebuild_whenIndexBlankAndVectorListNull() {
        VectorIndicesRepository vectorRepo = mock(VectorIndicesRepository.class);
        ModerationSimilarityConfigRepository configRepo = mock(ModerationSimilarityConfigRepository.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        RagPostIndexBuildService postBuild = mock(RagPostIndexBuildService.class);
        RagCommentIndexBuildService commentBuild = mock(RagCommentIndexBuildService.class);
        RagFileAssetIndexBuildService fileBuild = mock(RagFileAssetIndexBuildService.class);
        FakeModerationSamplesIndexConfigService indexConfigService = baseIndexConfigService();
        RetrievalRagProperties props = ragProps("   ", 256);
        InitialAdminIndexBootstrapService service = new InitialAdminIndexBootstrapService(
                vectorRepo, props, postBuild, commentBuild, fileBuild, syncService, configRepo, indexConfigService
        );

        when(configRepo.findAll()).thenReturn(List.of());
        when(vectorRepo.findByProvider(VectorIndexProvider.OTHER)).thenReturn(null);

        service.bootstrap(9L);

        ArgumentCaptor<ModerationSimilarityConfigEntity> captor = ArgumentCaptor.forClass(ModerationSimilarityConfigEntity.class);
        verify(configRepo).save(captor.capture());
        assertEquals(true, captor.getValue().getEnabled());
        assertEquals(5, captor.getValue().getDefaultTopK());
        assertEquals(0, captor.getValue().getMaxInputChars());
        assertEquals(0, captor.getValue().getDefaultNumCandidates());
        verify(vectorRepo, never()).existsByProviderAndCollectionName(any(), any());
        verify(syncService).reindexAll(true, 200, null);
        verify(postBuild, never()).rebuildPosts(any(), any(), any(), any(), any(), any(), any(), any());
        verify(commentBuild, never()).rebuildComments(any(), any(), any(), any(), any(), any());
        verify(fileBuild, never()).rebuildFiles(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void bootstrap_shouldSeedThreeDefaultVectorIndices_whenDimsInvalid() {
        VectorIndicesRepository vectorRepo = mock(VectorIndicesRepository.class);
        ModerationSimilarityConfigRepository configRepo = mock(ModerationSimilarityConfigRepository.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        RagPostIndexBuildService postBuild = mock(RagPostIndexBuildService.class);
        RagCommentIndexBuildService commentBuild = mock(RagCommentIndexBuildService.class);
        RagFileAssetIndexBuildService fileBuild = mock(RagFileAssetIndexBuildService.class);
        FakeModerationSamplesIndexConfigService indexConfigService = baseIndexConfigService();
        RetrievalRagProperties props = ragProps("rag_main", 0);
        InitialAdminIndexBootstrapService service = new InitialAdminIndexBootstrapService(
                vectorRepo, props, postBuild, commentBuild, fileBuild, syncService, configRepo, indexConfigService
        );
        ModerationSimilarityConfigEntity cfg = validCfg();

        when(configRepo.findAll()).thenReturn(List.of(cfg));
        when(vectorRepo.existsByProviderAndCollectionName(eq(VectorIndexProvider.OTHER), any())).thenReturn(false);
        when(vectorRepo.findByProvider(VectorIndexProvider.OTHER)).thenReturn(List.of());

        service.bootstrap(11L);

        ArgumentCaptor<VectorIndicesEntity> vectorCaptor = ArgumentCaptor.forClass(VectorIndicesEntity.class);
        verify(vectorRepo, times(3)).save(vectorCaptor.capture());
        List<VectorIndicesEntity> saved = vectorCaptor.getAllValues();
        assertEquals(1024, saved.get(0).getDim());
        assertEquals(1024, saved.get(1).getDim());
        assertEquals(1024, saved.get(2).getDim());
        assertEquals("POST", String.valueOf(saved.get(0).getMetadata().get("sourceType")));
        assertEquals("COMMENT", String.valueOf(saved.get(1).getMetadata().get("sourceType")));
        assertEquals("FILE_ASSET", String.valueOf(saved.get(2).getMetadata().get("sourceType")));
        verify(configRepo, never()).save(cfg);
    }

    @Test
    void bootstrap_shouldNormalizeExistingConfig_whenDefaultsNeedRepair() {
        VectorIndicesRepository vectorRepo = mock(VectorIndicesRepository.class);
        ModerationSimilarityConfigRepository configRepo = mock(ModerationSimilarityConfigRepository.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        RagPostIndexBuildService postBuild = mock(RagPostIndexBuildService.class);
        RagCommentIndexBuildService commentBuild = mock(RagCommentIndexBuildService.class);
        RagFileAssetIndexBuildService fileBuild = mock(RagFileAssetIndexBuildService.class);
        FakeModerationSamplesIndexConfigService indexConfigService = baseIndexConfigService();
        RetrievalRagProperties props = ragProps("rag_main", 384);
        InitialAdminIndexBootstrapService service = new InitialAdminIndexBootstrapService(
                vectorRepo, props, postBuild, commentBuild, fileBuild, syncService, configRepo, indexConfigService
        );
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setEnabled(true);
        cfg.setDefaultTopK(0);
        cfg.setMaxInputChars(-1);
        cfg.setDefaultNumCandidates(-1);

        when(configRepo.findAll()).thenReturn(List.of(cfg));
        when(vectorRepo.existsByProviderAndCollectionName(eq(VectorIndexProvider.OTHER), any())).thenReturn(true);
        when(vectorRepo.findByProvider(VectorIndexProvider.OTHER)).thenReturn(List.of());

        service.bootstrap(21L);

        verify(configRepo).save(cfg);
        assertEquals(5, cfg.getDefaultTopK());
        assertEquals(0, cfg.getMaxInputChars());
        assertEquals(0, cfg.getDefaultNumCandidates());
        verify(vectorRepo, never()).save(any(VectorIndicesEntity.class));
    }

    @Test
    void bootstrap_shouldKeepExistingConfig_whenDefaultsAlreadyValid() {
        VectorIndicesRepository vectorRepo = mock(VectorIndicesRepository.class);
        ModerationSimilarityConfigRepository configRepo = mock(ModerationSimilarityConfigRepository.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        RagPostIndexBuildService postBuild = mock(RagPostIndexBuildService.class);
        RagCommentIndexBuildService commentBuild = mock(RagCommentIndexBuildService.class);
        RagFileAssetIndexBuildService fileBuild = mock(RagFileAssetIndexBuildService.class);
        FakeModerationSamplesIndexConfigService indexConfigService = baseIndexConfigService();
        RetrievalRagProperties props = ragProps(" ", 64);
        InitialAdminIndexBootstrapService service = new InitialAdminIndexBootstrapService(
                vectorRepo, props, postBuild, commentBuild, fileBuild, syncService, configRepo, indexConfigService
        );
        ModerationSimilarityConfigEntity cfg = validCfg();
        when(configRepo.findAll()).thenReturn(List.of(cfg));
        when(vectorRepo.findByProvider(VectorIndexProvider.OTHER)).thenReturn(null);

        service.bootstrap(33L);

        verify(configRepo, never()).save(cfg);
    }

    @Test
    void bootstrap_shouldRouteRagRebuildBySourceType_andHandleExceptions() {
        VectorIndicesRepository vectorRepo = mock(VectorIndicesRepository.class);
        ModerationSimilarityConfigRepository configRepo = mock(ModerationSimilarityConfigRepository.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        RagPostIndexBuildService postBuild = mock(RagPostIndexBuildService.class);
        RagCommentIndexBuildService commentBuild = mock(RagCommentIndexBuildService.class);
        RagFileAssetIndexBuildService fileBuild = mock(RagFileAssetIndexBuildService.class);
        FakeModerationSamplesIndexConfigService indexConfigService = baseIndexConfigService();
        RetrievalRagProperties props = ragProps("fallback_idx", 256);
        InitialAdminIndexBootstrapService service = new InitialAdminIndexBootstrapService(
                vectorRepo, props, postBuild, commentBuild, fileBuild, syncService, configRepo, indexConfigService
        );
        ModerationSimilarityConfigEntity cfg = validCfg();
        VectorIndicesEntity commentOk = vectorIndex(1L, "c_ok", 128, "COMMENT");
        VectorIndicesEntity commentFail = vectorIndex(2L, "c_fail", 128, "COMMENT");
        VectorIndicesEntity fileOk = vectorIndex(3L, "f_ok", 256, "FILE_ASSET");
        VectorIndicesEntity fileFail = vectorIndex(4L, "f_fail", 256, "FILE_ASSET");
        VectorIndicesEntity postFail = vectorIndex(5L, "p_fail", 512, "   ");
        VectorIndicesEntity postOkMetaNull = vectorIndex(6L, "p_ok", 512, null);

        when(configRepo.findAll()).thenReturn(List.of(cfg));
        when(vectorRepo.existsByProviderAndCollectionName(eq(VectorIndexProvider.OTHER), any())).thenReturn(true);
        when(syncService.reindexAll(true, 200, null)).thenThrow(new RuntimeException("sync-fail"));
        when(vectorRepo.findByProvider(VectorIndexProvider.OTHER)).thenReturn(Arrays.asList(
                null, commentOk, commentFail, fileOk, fileFail, postFail, postOkMetaNull
        ));
        when(commentBuild.rebuildComments(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id != null && id == 2L) throw new RuntimeException("comment-fail");
            return null;
        });
        when(fileBuild.rebuildFiles(any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id != null && id == 4L) throw new RuntimeException("file-fail");
            return null;
        });
        when(postBuild.rebuildPosts(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id != null && id == 5L) throw new RuntimeException("post-fail");
            return null;
        });

        service.bootstrap(55L);

        verify(commentBuild, times(2)).rebuildComments(any(), any(), any(), any(), any(), any());
        verify(fileBuild, times(2)).rebuildFiles(any(), any(), any(), any(), any(), any(), any());
        verify(postBuild, times(2)).rebuildPosts(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void bootstrap_shouldCoverNullConfigFieldsAndMetadataSourceTypeNullBranch() {
        VectorIndicesRepository vectorRepo = mock(VectorIndicesRepository.class);
        ModerationSimilarityConfigRepository configRepo = mock(ModerationSimilarityConfigRepository.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        RagPostIndexBuildService postBuild = mock(RagPostIndexBuildService.class);
        RagCommentIndexBuildService commentBuild = mock(RagCommentIndexBuildService.class);
        RagFileAssetIndexBuildService fileBuild = mock(RagFileAssetIndexBuildService.class);
        FakeModerationSamplesIndexConfigService indexConfigService = baseIndexConfigService();
        RetrievalRagProperties props = ragProps("fallback_idx", 128);
        InitialAdminIndexBootstrapService service = new InitialAdminIndexBootstrapService(
                vectorRepo, props, postBuild, commentBuild, fileBuild, syncService, configRepo, indexConfigService
        );
        ModerationSimilarityConfigEntity cfg = validCfg();
        cfg.setDefaultTopK(null);
        cfg.setMaxInputChars(null);
        cfg.setDefaultNumCandidates(null);
        VectorIndicesEntity sourceTypeMissing = vectorIndex(88L, "meta_missing_source_type", 64, "");
        sourceTypeMissing.setMetadata(new HashMap<>());

        when(configRepo.findAll()).thenReturn(List.of(cfg));
        when(vectorRepo.existsByProviderAndCollectionName(eq(VectorIndexProvider.OTHER), any())).thenReturn(true);
        when(vectorRepo.findByProvider(VectorIndexProvider.OTHER)).thenReturn(List.of(sourceTypeMissing));

        service.bootstrap(77L);

        verify(configRepo).save(cfg);
        verify(postBuild).rebuildPosts(eq(88L), any(), any(), any(), any(), any(), any(), eq(64));
    }

    @Test
    void bootstrap_shouldSkipRebuildEntry_whenCollectionAndFallbackIndexBothBlank() {
        VectorIndicesRepository vectorRepo = mock(VectorIndicesRepository.class);
        ModerationSimilarityConfigRepository configRepo = mock(ModerationSimilarityConfigRepository.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        RagPostIndexBuildService postBuild = mock(RagPostIndexBuildService.class);
        RagCommentIndexBuildService commentBuild = mock(RagCommentIndexBuildService.class);
        RagFileAssetIndexBuildService fileBuild = mock(RagFileAssetIndexBuildService.class);
        FakeModerationSamplesIndexConfigService indexConfigService = baseIndexConfigService();
        RetrievalRagProperties props = ragProps(" ", 128);
        InitialAdminIndexBootstrapService service = new InitialAdminIndexBootstrapService(
                vectorRepo, props, postBuild, commentBuild, fileBuild, syncService, configRepo, indexConfigService
        );
        ModerationSimilarityConfigEntity cfg = validCfg();
        VectorIndicesEntity noIndex = vectorIndex(77L, "   ", 64, "COMMENT");

        when(configRepo.findAll()).thenReturn(List.of(cfg));
        when(vectorRepo.findByProvider(VectorIndexProvider.OTHER)).thenReturn(List.of(noIndex));

        service.bootstrap(66L);

        verify(commentBuild, never()).rebuildComments(any(), any(), any(), any(), any(), any());
        verify(fileBuild, never()).rebuildFiles(any(), any(), any(), any(), any(), any(), any());
        verify(postBuild, never()).rebuildPosts(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureDefaultVectorIndex_shouldReturnImmediately_whenCollectionNameBlank() throws Exception {
        VectorIndicesRepository vectorRepo = mock(VectorIndicesRepository.class);
        InitialAdminIndexBootstrapService service = new InitialAdminIndexBootstrapService(
                vectorRepo,
                ragProps("rag_main", 128),
                mock(RagPostIndexBuildService.class),
                mock(RagCommentIndexBuildService.class),
                mock(RagFileAssetIndexBuildService.class),
                mock(ModerationSamplesSyncService.class),
                mock(ModerationSimilarityConfigRepository.class),
                baseIndexConfigService()
        );

        Method method = InitialAdminIndexBootstrapService.class.getDeclaredMethod(
                "ensureDefaultVectorIndex", String.class, int.class, String.class, int.class, int.class, String.class
        );
        method.setAccessible(true);
        method.invoke(service, "   ", 128, "POST", 800, 80, "x");

        verify(vectorRepo, never()).existsByProviderAndCollectionName(any(), any());
        verify(vectorRepo, never()).save(any(VectorIndicesEntity.class));
    }

    @Test
    void toNonBlank_shouldHandleNullBlankAndTrimmedValues() throws Exception {
        Method method = InitialAdminIndexBootstrapService.class.getDeclaredMethod("toNonBlank", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, new Object[]{null}));
        assertNull(method.invoke(null, "   "));
        assertEquals("abc", method.invoke(null, "  abc  "));
    }

    private static RetrievalRagProperties ragProps(String index, int dims) {
        RetrievalRagProperties props = new RetrievalRagProperties();
        props.getEs().setIndex(index);
        props.getEs().setEmbeddingDims(dims);
        return props;
    }

    private static ModerationSimilarityConfigEntity validCfg() {
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setEnabled(true);
        cfg.setDefaultTopK(5);
        cfg.setMaxInputChars(100);
        cfg.setDefaultNumCandidates(10);
        return cfg;
    }

    private static FakeModerationSamplesIndexConfigService baseIndexConfigService() {
        return new FakeModerationSamplesIndexConfigService();
    }

    private static VectorIndicesEntity vectorIndex(Long id, String collectionName, Integer dim, String sourceType) {
        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setId(id);
        vi.setCollectionName(collectionName);
        vi.setDim(dim);
        if (sourceType == null) {
            vi.setMetadata(null);
        } else {
            Map<String, Object> meta = new HashMap<>();
            meta.put("sourceType", sourceType);
            vi.setMetadata(meta);
        }
        return vi;
    }

    private static class FakeModerationSamplesIndexConfigService extends ModerationSamplesIndexConfigService {
        String embeddingModel = "embed-default";
        int embeddingDims = 128;
        String indexName = "moderation_samples_v1";

        private FakeModerationSamplesIndexConfigService() {
            super(mock(ModerationSamplesIndexConfigRepository.class));
        }

        @Override
        public ModerationSamplesIndexConfigEntity getOrSeedDefault(Long updatedBy) {
            return new ModerationSamplesIndexConfigEntity();
        }

        @Override
        public String getIndexNameOrDefault() {
            return indexName;
        }

        @Override
        public String getEmbeddingModelOrDefault() {
            return embeddingModel;
        }

        @Override
        public int getEmbeddingDimsOrDefault() {
            return embeddingDims;
        }
    }
}
