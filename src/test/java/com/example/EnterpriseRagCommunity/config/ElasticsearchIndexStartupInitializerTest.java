package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ElasticsearchIndexStartupInitializerTest {

    @Test
    void init_when_disabled_should_return_early() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        RagPostsIndexService ragPostsIndexService = mock(RagPostsIndexService.class);
        RagCommentsIndexService ragCommentsIndexService = mock(RagCommentsIndexService.class);
        RagFileAssetsIndexService ragFileAssetsIndexService = mock(RagFileAssetsIndexService.class);
        ModerationSamplesIndexService moderationSamplesIndexService = mock(ModerationSamplesIndexService.class);
        ModerationSamplesIndexConfigService moderationSamplesIndexConfigService = mock(ModerationSamplesIndexConfigService.class);
        ModerationSimilarityConfigRepository moderationSimilarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        ElasticsearchIndexStartupInitializer initializer = new ElasticsearchIndexStartupInitializer(
                vectorIndicesRepository,
                ragProps,
                ragPostsIndexService,
                ragCommentsIndexService,
                ragFileAssetsIndexService,
                moderationSamplesIndexService,
                moderationSamplesIndexConfigService,
                moderationSimilarityConfigRepository,
                systemConfigurationService
        );
        ReflectionTestUtils.setField(initializer, "enabled", false);

        initializer.init();

        verifyNoInteractions(systemConfigurationService);
        verifyNoInteractions(vectorIndicesRepository);
        verifyNoInteractions(moderationSimilarityConfigRepository);
        verifyNoInteractions(moderationSamplesIndexService);
        verifyNoInteractions(ragPostsIndexService);
        verifyNoInteractions(ragCommentsIndexService);
        verifyNoInteractions(ragFileAssetsIndexService);
    }

    @Test
    void init_when_api_key_missing_should_skip_all_initialization() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        RagPostsIndexService ragPostsIndexService = mock(RagPostsIndexService.class);
        RagCommentsIndexService ragCommentsIndexService = mock(RagCommentsIndexService.class);
        RagFileAssetsIndexService ragFileAssetsIndexService = mock(RagFileAssetsIndexService.class);
        ModerationSamplesIndexService moderationSamplesIndexService = mock(ModerationSamplesIndexService.class);
        ModerationSamplesIndexConfigService moderationSamplesIndexConfigService = mock(ModerationSamplesIndexConfigService.class);
        ModerationSimilarityConfigRepository moderationSimilarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("   ");

        ElasticsearchIndexStartupInitializer initializer = new ElasticsearchIndexStartupInitializer(
                vectorIndicesRepository,
                ragProps,
                ragPostsIndexService,
                ragCommentsIndexService,
                ragFileAssetsIndexService,
                moderationSamplesIndexService,
                moderationSamplesIndexConfigService,
                moderationSimilarityConfigRepository,
                systemConfigurationService
        );
        ReflectionTestUtils.setField(initializer, "enabled", true);

        initializer.init();

        verify(systemConfigurationService).getConfig(eq("APP_ES_API_KEY"));
        verifyNoInteractions(vectorIndicesRepository);
        verifyNoInteractions(moderationSimilarityConfigRepository);
        verifyNoInteractions(moderationSamplesIndexService);
        verifyNoInteractions(ragPostsIndexService);
        verifyNoInteractions(ragCommentsIndexService);
        verifyNoInteractions(ragFileAssetsIndexService);
    }

    @Test
    void init_should_skip_when_embedding_dims_not_configured() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingDims(0);
        ragProps.getEs().setIndex("rag_post_chunks_v1");
        RagPostsIndexService ragPostsIndexService = mock(RagPostsIndexService.class);
        RagCommentsIndexService ragCommentsIndexService = mock(RagCommentsIndexService.class);
        RagFileAssetsIndexService ragFileAssetsIndexService = mock(RagFileAssetsIndexService.class);
        ModerationSamplesIndexService moderationSamplesIndexService = mock(ModerationSamplesIndexService.class);
        ModerationSamplesIndexConfigService moderationSamplesIndexConfigService = mock(ModerationSamplesIndexConfigService.class);
        ModerationSimilarityConfigRepository moderationSimilarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");
        when(moderationSimilarityConfigRepository.findAll()).thenReturn(List.of());
        when(moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault()).thenReturn(0);
        when(vectorIndicesRepository.findByProvider(eq(VectorIndexProvider.OTHER))).thenReturn(null);

        ElasticsearchIndexStartupInitializer initializer = new ElasticsearchIndexStartupInitializer(
                vectorIndicesRepository,
                ragProps,
                ragPostsIndexService,
                ragCommentsIndexService,
                ragFileAssetsIndexService,
                moderationSamplesIndexService,
                moderationSamplesIndexConfigService,
                moderationSimilarityConfigRepository,
                systemConfigurationService
        );
        ReflectionTestUtils.setField(initializer, "enabled", true);

        initializer.init();

        verify(moderationSamplesIndexService, never()).ensureIndex(anyInt());
        verify(moderationSamplesIndexService, never()).recreateIndex(anyInt());
        verifyNoInteractions(ragPostsIndexService);
        verifyNoInteractions(ragCommentsIndexService);
        verifyNoInteractions(ragFileAssetsIndexService);
    }

    @Test
    void init_when_force_recreate_and_moderation_init_fails_should_continue() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingDims(128);
        ragProps.getEs().setIndex("rag_post_chunks_v1");
        RagPostsIndexService ragPostsIndexService = mock(RagPostsIndexService.class);
        RagCommentsIndexService ragCommentsIndexService = mock(RagCommentsIndexService.class);
        RagFileAssetsIndexService ragFileAssetsIndexService = mock(RagFileAssetsIndexService.class);
        ModerationSamplesIndexService moderationSamplesIndexService = mock(ModerationSamplesIndexService.class);
        ModerationSamplesIndexConfigService moderationSamplesIndexConfigService = mock(ModerationSamplesIndexConfigService.class);
        ModerationSimilarityConfigRepository moderationSimilarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");
        when(moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault()).thenReturn(256);
        doThrow(new RuntimeException("boom")).when(moderationSamplesIndexService).recreateIndex(eq(256));
        when(vectorIndicesRepository.findByProvider(eq(VectorIndexProvider.OTHER))).thenReturn(List.of());

        ElasticsearchIndexStartupInitializer initializer = new ElasticsearchIndexStartupInitializer(
                vectorIndicesRepository,
                ragProps,
                ragPostsIndexService,
                ragCommentsIndexService,
                ragFileAssetsIndexService,
                moderationSamplesIndexService,
                moderationSamplesIndexConfigService,
                moderationSimilarityConfigRepository,
                systemConfigurationService
        );
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "forceRecreate", true);
        ReflectionTestUtils.setField(initializer, "failOnError", false);

        initializer.init();

        verify(moderationSamplesIndexService).recreateIndex(eq(256));
        verify(ragPostsIndexService).recreateIndex(eq("rag_post_chunks_v1"), eq(128));
    }

    @Test
    void init_when_default_index_ensure_throws_should_fallback_to_recreate() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingDims(128);
        ragProps.getEs().setIndex("rag_post_chunks_v1");
        RagPostsIndexService ragPostsIndexService = mock(RagPostsIndexService.class);
        RagCommentsIndexService ragCommentsIndexService = mock(RagCommentsIndexService.class);
        RagFileAssetsIndexService ragFileAssetsIndexService = mock(RagFileAssetsIndexService.class);
        ModerationSamplesIndexService moderationSamplesIndexService = mock(ModerationSamplesIndexService.class);
        ModerationSamplesIndexConfigService moderationSamplesIndexConfigService = mock(ModerationSamplesIndexConfigService.class);
        ModerationSimilarityConfigRepository moderationSimilarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");
        when(moderationSimilarityConfigRepository.findAll()).thenReturn(List.of());
        when(moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault()).thenReturn(0);
        when(vectorIndicesRepository.findByProvider(eq(VectorIndexProvider.OTHER))).thenReturn(List.of());

        doThrow(new IllegalStateException("mismatch")).when(ragPostsIndexService).ensureIndex(eq("rag_post_chunks_v1"), eq(128));
        doNothing().when(ragPostsIndexService).recreateIndex(eq("rag_post_chunks_v1"), eq(128));

        ElasticsearchIndexStartupInitializer initializer = new ElasticsearchIndexStartupInitializer(
                vectorIndicesRepository,
                ragProps,
                ragPostsIndexService,
                ragCommentsIndexService,
                ragFileAssetsIndexService,
                moderationSamplesIndexService,
                moderationSamplesIndexConfigService,
                moderationSimilarityConfigRepository,
                systemConfigurationService
        );
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "forceRecreate", false);
        ReflectionTestUtils.setField(initializer, "failOnError", false);

        initializer.init();

        verify(ragPostsIndexService).ensureIndex(eq("rag_post_chunks_v1"), eq(128));
        verify(ragPostsIndexService).recreateIndex(eq("rag_post_chunks_v1"), eq(128));
    }

    @Test
    void init_when_vector_indices_dims_conflict_should_skip_index() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingDims(128);
        ragProps.getEs().setIndex("fallback");
        RagPostsIndexService ragPostsIndexService = mock(RagPostsIndexService.class);
        RagCommentsIndexService ragCommentsIndexService = mock(RagCommentsIndexService.class);
        RagFileAssetsIndexService ragFileAssetsIndexService = mock(RagFileAssetsIndexService.class);
        ModerationSamplesIndexService moderationSamplesIndexService = mock(ModerationSamplesIndexService.class);
        ModerationSamplesIndexConfigService moderationSamplesIndexConfigService = mock(ModerationSamplesIndexConfigService.class);
        ModerationSimilarityConfigRepository moderationSimilarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");
        when(moderationSimilarityConfigRepository.findAll()).thenReturn(List.of());
        when(moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault()).thenReturn(0);

        VectorIndicesEntity v1 = new VectorIndicesEntity();
        v1.setProvider(VectorIndexProvider.OTHER);
        v1.setCollectionName("idx");
        v1.setDim(128);
        VectorIndicesEntity v2 = new VectorIndicesEntity();
        v2.setProvider(VectorIndexProvider.OTHER);
        v2.setCollectionName("idx");
        v2.setDim(256);
        when(vectorIndicesRepository.findByProvider(eq(VectorIndexProvider.OTHER))).thenReturn(List.of(v1, v2));

        ElasticsearchIndexStartupInitializer initializer = new ElasticsearchIndexStartupInitializer(
                vectorIndicesRepository,
                ragProps,
                ragPostsIndexService,
                ragCommentsIndexService,
                ragFileAssetsIndexService,
                moderationSamplesIndexService,
                moderationSamplesIndexConfigService,
                moderationSimilarityConfigRepository,
                systemConfigurationService
        );
        ReflectionTestUtils.setField(initializer, "enabled", true);

        initializer.init();

        verifyNoInteractions(ragPostsIndexService);
        verifyNoInteractions(ragCommentsIndexService);
        verifyNoInteractions(ragFileAssetsIndexService);
    }

    @Test
    void init_should_route_by_source_type_and_handle_temp_skip() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingDims(128);
        ragProps.getEs().setIndex("fallback");
        ragProps.getEs().setIkEnabled(true);
        RagPostsIndexService ragPostsIndexService = mock(RagPostsIndexService.class);
        RagCommentsIndexService ragCommentsIndexService = mock(RagCommentsIndexService.class);
        RagFileAssetsIndexService ragFileAssetsIndexService = mock(RagFileAssetsIndexService.class);
        ModerationSamplesIndexService moderationSamplesIndexService = mock(ModerationSamplesIndexService.class);
        ModerationSamplesIndexConfigService moderationSamplesIndexConfigService = mock(ModerationSamplesIndexConfigService.class);
        ModerationSimilarityConfigRepository moderationSimilarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");
        when(moderationSimilarityConfigRepository.findAll()).thenReturn(List.of());
        when(moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault()).thenReturn(0);

        VectorIndicesEntity comment = new VectorIndicesEntity();
        comment.setProvider(VectorIndexProvider.OTHER);
        comment.setCollectionName("cidx");
        comment.setDim(10);
        comment.setMetadata(Map.of("sourceType", "COMMENT"));

        VectorIndicesEntity file = new VectorIndicesEntity();
        file.setProvider(VectorIndexProvider.OTHER);
        file.setCollectionName("fidx");
        file.setDim(11);
        file.setMetadata(Map.of("sourceType", "FILE_ASSET"));

        VectorIndicesEntity temp = new VectorIndicesEntity();
        temp.setProvider(VectorIndexProvider.OTHER);
        temp.setCollectionName("tidx");
        temp.setDim(12);
        temp.setMetadata(Map.of("sourceType", "TEMP"));

        VectorIndicesEntity post = new VectorIndicesEntity();
        post.setProvider(VectorIndexProvider.OTHER);
        post.setCollectionName("pidx");
        post.setDim(13);
        post.setMetadata(Map.of("sourceType", "POST"));

        when(vectorIndicesRepository.findByProvider(eq(VectorIndexProvider.OTHER))).thenReturn(List.of(comment, file, temp, post));

        ElasticsearchIndexStartupInitializer initializer = new ElasticsearchIndexStartupInitializer(
                vectorIndicesRepository,
                ragProps,
                ragPostsIndexService,
                ragCommentsIndexService,
                ragFileAssetsIndexService,
                moderationSamplesIndexService,
                moderationSamplesIndexConfigService,
                moderationSimilarityConfigRepository,
                systemConfigurationService
        );
        ReflectionTestUtils.setField(initializer, "enabled", true);

        initializer.init();

        verify(ragCommentsIndexService).ensureIndex(eq("cidx"), eq(10));
        verify(ragFileAssetsIndexService).ensureIndex(eq("fidx"), eq(11), eq(true));
        verify(ragPostsIndexService).ensureIndex(eq("pidx"), eq(13));
        verify(ragPostsIndexService, never()).ensureIndex(eq("tidx"), eq(12));
    }

    @Test
    void init_when_fail_on_error_true_should_rethrow() {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setEmbeddingDims(128);
        ragProps.getEs().setIndex("rag_post_chunks_v1");
        RagPostsIndexService ragPostsIndexService = mock(RagPostsIndexService.class);
        RagCommentsIndexService ragCommentsIndexService = mock(RagCommentsIndexService.class);
        RagFileAssetsIndexService ragFileAssetsIndexService = mock(RagFileAssetsIndexService.class);
        ModerationSamplesIndexService moderationSamplesIndexService = mock(ModerationSamplesIndexService.class);
        ModerationSamplesIndexConfigService moderationSamplesIndexConfigService = mock(ModerationSamplesIndexConfigService.class);
        ModerationSimilarityConfigRepository moderationSimilarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");
        when(moderationSimilarityConfigRepository.findAll()).thenReturn(List.of());
        when(moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault()).thenReturn(0);
        when(vectorIndicesRepository.findByProvider(eq(VectorIndexProvider.OTHER))).thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(ragPostsIndexService).recreateIndex(eq("rag_post_chunks_v1"), eq(128));

        ElasticsearchIndexStartupInitializer initializer = new ElasticsearchIndexStartupInitializer(
                vectorIndicesRepository,
                ragProps,
                ragPostsIndexService,
                ragCommentsIndexService,
                ragFileAssetsIndexService,
                moderationSamplesIndexService,
                moderationSamplesIndexConfigService,
                moderationSimilarityConfigRepository,
                systemConfigurationService
        );
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "forceRecreate", true);
        ReflectionTestUtils.setField(initializer, "failOnError", true);

        assertThrows(RuntimeException.class, initializer::init);
    }
}

