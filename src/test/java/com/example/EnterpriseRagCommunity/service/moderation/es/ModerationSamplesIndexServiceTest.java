package com.example.EnterpriseRagCommunity.service.moderation.es;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.Query;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.service.es.ElasticsearchIkAnalyzerProbe;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;

class ModerationSamplesIndexServiceTest {

    private ElasticsearchTemplate template;
    private ModerationSamplesIndexConfigService indexConfigService;
    private ModerationSimilarityConfigRepository similarityConfigRepository;
    private ElasticsearchIkAnalyzerProbe ikProbe;
    private DependencyIsolationGuard dependencyIsolationGuard;
    private DependencyCircuitBreakerService dependencyCircuitBreakerService;
    private IndexOperations indexOperations;
    private ModerationSamplesIndexService service;

    @BeforeEach
    void setUp() {
        template = mock(ElasticsearchTemplate.class);
        indexConfigService = mock(ModerationSamplesIndexConfigService.class);
        similarityConfigRepository = mock(ModerationSimilarityConfigRepository.class);
        ikProbe = mock(ElasticsearchIkAnalyzerProbe.class);
        dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);
        indexOperations = mock(IndexOperations.class);

        when(indexConfigService.getIndexNameOrDefault()).thenReturn("moderation_es");
        when(template.indexOps(any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(indexOperations);
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(dependencyCircuitBreakerService).run(eq("ES"), any(Supplier.class));

        service = new ModerationSamplesIndexService(
                template,
                indexConfigService,
                similarityConfigRepository,
                ikProbe,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );
    }

    @Test
    void indexExists_shouldReturnOpsExists() {
        when(indexOperations.exists()).thenReturn(true);

        boolean out = service.indexExists();

        assertThat(out).isTrue();
        verify(dependencyIsolationGuard).requireElasticsearchAllowed();
    }

    @Test
    void getEmbeddingDimsInMapping_shouldReturnNullWhenIndexMissing() {
        when(indexOperations.exists()).thenReturn(false);

        Integer out = service.getEmbeddingDimsInMapping();

        assertThat(out).isNull();
    }

    @Test
    void getEmbeddingDimsInMapping_shouldReadDimsFromPropertiesAndMappings() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of("embedding", Map.of("dims", 8))));
        assertThat(service.getEmbeddingDimsInMapping()).isEqualTo(8);

        when(indexOperations.getMapping()).thenReturn(Map.of("mappings", Map.of("properties", Map.of("embedding", Map.of("dims", " 16 ")))));
        assertThat(service.getEmbeddingDimsInMapping()).isEqualTo(16);
    }

    @Test
    void getEmbeddingDimsInMapping_shouldReturnNullOnBadMappingOrError() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of("embedding", Map.of("dims", "xx"))));
        assertThat(service.getEmbeddingDimsInMapping()).isNull();

        when(indexOperations.getMapping()).thenThrow(new RuntimeException("mapping fail"));
        assertThat(service.getEmbeddingDimsInMapping()).isNull();
    }

    @Test
    void countDocs_shouldReturnNullOnCircuitBreakerError() {
        when(dependencyCircuitBreakerService.run(eq("ES"), any(Supplier.class)))
                .thenThrow(new RuntimeException("cb fail"));

        Long out = service.countDocs();

        assertThat(out).isNull();
    }

    @Test
    void countDocs_shouldCallTemplateCount() {
        when(template.count(any(Query.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(12L);

        Long out = service.countDocs();

        assertThat(out).isEqualTo(12L);
    }

    @Test
    void ensureIndex_shouldRecreateWhenDimsMismatch() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of("embedding", Map.of("dims", 4))));
        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(false);

        service.ensureIndex(8);

        verify(indexOperations).delete();
        verify(indexOperations).create(any(Document.class));
        verify(indexOperations).putMapping(any(Document.class));
    }

    @Test
    void ensureIndex_shouldSkipRecreateWhenDimsSame() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of("embedding", Map.of("dims", 8))));

        service.ensureIndex(8);

        verify(indexOperations, never()).delete();
    }

    @Test
    void ensureIndex_shouldSkipRecreateWhenEmbeddingDimsNotPositive() {
        when(indexOperations.exists()).thenReturn(true);

        service.ensureIndex(0);

        verify(indexOperations, never()).delete();
        verify(indexOperations, never()).create(any(Document.class));
    }

    @Test
    void ensureIndex_shouldRecreateWhenExistingDimsMissing() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getMapping()).thenReturn(Map.of("properties", Map.of()));
        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(false);

        service.ensureIndex(6);

        verify(indexOperations).delete();
        verify(indexOperations).create(any(Document.class));
    }

    @Test
    void ensureIndex_shouldCreateWhenIndexMissing() {
        when(indexOperations.exists()).thenReturn(false);
        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(false);

        service.ensureIndex(6);

        verify(indexOperations).create(any(Document.class));
        verify(indexOperations).putMapping(any(Document.class));
    }

    @Test
    void recreateIndex_shouldDeleteThenCreate() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(false);

        service.recreateIndex(5);

        verify(indexOperations).delete();
        verify(indexOperations).create(any(Document.class));
    }

    @Test
    void recreateIndex_shouldCreateWithoutDeleteWhenMissing() {
        when(indexOperations.exists()).thenReturn(false);
        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(false);

        service.recreateIndex(5);

        verify(indexOperations, never()).delete();
        verify(indexOperations).create(any(Document.class));
    }

    @Test
    void ensureIndex_withoutArg_shouldUseSimilarityDimsThenFallback() {
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setEmbeddingDims(11);
        when(similarityConfigRepository.findAll()).thenReturn(List.of(cfg));
        when(indexOperations.exists()).thenReturn(false);
        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(false);

        service.ensureIndex();

        verify(indexOperations).putMapping(any(Document.class));

        when(similarityConfigRepository.findAll()).thenThrow(new RuntimeException("db err"));
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(-3);
        service.ensureIndex();
        verify(indexConfigService).getEmbeddingDimsOrDefault();
    }

    @Test
    void ensureIndex_withoutArg_shouldFallbackWhenSimilarityDimsNonPositive() {
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setEmbeddingDims(0);
        when(similarityConfigRepository.findAll()).thenReturn(List.of(cfg));
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(4);
        when(indexOperations.exists()).thenReturn(false);
        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(false);

        service.ensureIndex();

        verify(indexConfigService).getEmbeddingDimsOrDefault();
        verify(indexOperations).create(any(Document.class));
    }

    @Test
    void resolveIkEnabled_shouldHandleConfiguredAndProbeStates() throws Exception {
        Method m = ModerationSamplesIndexService.class.getDeclaredMethod("resolveIkEnabled");
        m.setAccessible(true);

        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(false);
        assertThat((Boolean) m.invoke(service)).isFalse();

        when(indexConfigService.isIkEnabledOrDefault()).thenReturn(true);
        when(ikProbe.isIkSupported()).thenReturn(false);
        assertThat((Boolean) m.invoke(service)).isFalse();

        when(ikProbe.isIkSupported()).thenReturn(true);
        assertThat((Boolean) m.invoke(service)).isTrue();

        when(ikProbe.isIkSupported()).thenReturn(false);
        assertThat((Boolean) m.invoke(service)).isFalse();
    }

    @Test
    void extractEmbeddingDims_static_shouldCoverEdgeCases() throws Exception {
        Method m = ModerationSamplesIndexService.class.getDeclaredMethod("extractEmbeddingDims", Map.class);
        m.setAccessible(true);

        assertThat((Integer) m.invoke(null, (Object) null)).isNull();
        assertThat((Integer) m.invoke(null, Map.of("properties", "x"))).isNull();
        assertThat((Integer) m.invoke(null, Map.of("properties", Map.of("embedding", Map.of("dims", " "))))).isNull();
        assertThat((Integer) m.invoke(null, Map.of("properties", Map.of("embedding", Map.of("dims", "abc"))))).isNull();
        assertThat((Integer) m.invoke(null, Map.of("properties", Map.of("embedding", Map.of("dims", true))))).isNull();
        assertThat((Integer) m.invoke(null, Map.of("properties", Map.of("embedding", Map.of("dims", 3))))).isEqualTo(3);
        assertThat((Integer) m.invoke(null, Map.of("mappings", "x"))).isNull();
        assertThat((Integer) m.invoke(null, Map.of("mappings", Map.of("properties", "x")))).isNull();
    }

    @Test
    void resolveDefaultEmbeddingDims_shouldCoverNullAndPositive() throws Exception {
        Method m = ModerationSamplesIndexService.class.getDeclaredMethod("resolveDefaultEmbeddingDims");
        m.setAccessible(true);

        when(similarityConfigRepository.findAll()).thenReturn(List.of());
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(0);
        assertThat((Integer) m.invoke(service)).isEqualTo(0);

        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setEmbeddingDims(12);
        when(similarityConfigRepository.findAll()).thenReturn(List.of(cfg));
        assertThat((Integer) m.invoke(service)).isEqualTo(12);

        ModerationSimilarityConfigEntity cfgWithNull = new ModerationSimilarityConfigEntity();
        cfgWithNull.setEmbeddingDims(null);
        when(similarityConfigRepository.findAll()).thenReturn(List.of(cfgWithNull));
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(5);
        assertThat((Integer) m.invoke(service)).isEqualTo(5);
    }

    @Test
    void buildMapping_shouldIncludeAnalyzerAndEmbeddingByFlags() throws Exception {
        Method m = ModerationSamplesIndexService.class.getDeclaredMethod("buildMapping", boolean.class, int.class);
        m.setAccessible(true);

        Map<String, Object> withIk = (Map<String, Object>) m.invoke(service, true, 7);
        Map<String, Object> props1 = (Map<String, Object>) withIk.get("properties");
        Map<String, Object> norm1 = (Map<String, Object>) props1.get("normalized_text");
        assertThat(norm1.get("analyzer")).isEqualTo("ik_max_word");
        assertThat(((Map<String, Object>) props1.get("embedding")).get("dims")).isEqualTo(7);

        Map<String, Object> noIkNoEmb = (Map<String, Object>) m.invoke(service, false, 0);
        Map<String, Object> props2 = (Map<String, Object>) noIkNoEmb.get("properties");
        Map<String, Object> norm2 = (Map<String, Object>) props2.get("normalized_text");
        assertThat(norm2).doesNotContainKey("analyzer");
        assertThat(props2).doesNotContainKey("embedding");
    }

    @Test
    void tryCreate_shouldFallbackWhenIkCreateFails() throws Exception {
        Method m = ModerationSamplesIndexService.class.getDeclaredMethod("tryCreate", IndexOperations.class, boolean.class, int.class);
        m.setAccessible(true);

        when(indexOperations.create(any(Document.class)))
                .thenThrow(new RuntimeException("ik fail"))
                .thenReturn(true);
        when(indexOperations.exists()).thenReturn(true);

        m.invoke(service, indexOperations, true, 9);

        verify(indexOperations).exists();
        verify(indexOperations).delete();
        verify(indexOperations).putMapping(any(Document.class));
    }

    @Test
    void tryCreate_shouldFallbackWithoutDeleteWhenIndexNotExists() throws Exception {
        Method m = ModerationSamplesIndexService.class.getDeclaredMethod("tryCreate", IndexOperations.class, boolean.class, int.class);
        m.setAccessible(true);

        when(indexOperations.create(any(Document.class)))
                .thenThrow(new RuntimeException("ik fail"))
                .thenReturn(true);
        when(indexOperations.exists()).thenReturn(false);

        m.invoke(service, indexOperations, true, 7);

        verify(indexOperations).exists();
        verify(indexOperations, never()).delete();
        verify(indexOperations).putMapping(any(Document.class));
    }

    @Test
    void tryCreate_shouldRethrowWhenIkDisabledAndCreateFails() throws Exception {
        Method m = ModerationSamplesIndexService.class.getDeclaredMethod("tryCreate", IndexOperations.class, boolean.class, int.class);
        m.setAccessible(true);
        when(indexOperations.create(any(Document.class))).thenThrow(new RuntimeException("create fail"));

        assertThatThrownBy(() -> m.invoke(service, indexOperations, false, 5))
                .hasRootCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void buildSettings_shouldIncludeAnalysisWhenIkEnabled() throws Exception {
        Method m = ModerationSamplesIndexService.class.getDeclaredMethod("buildSettings", boolean.class);
        m.setAccessible(true);

        Map<String, Object> withIk = (Map<String, Object>) m.invoke(service, true);
        Map<String, Object> noIk = (Map<String, Object>) m.invoke(service, false);

        assertThat(withIk).containsKey("analysis");
        assertThat(noIk).doesNotContainKey("analysis");
    }
}
