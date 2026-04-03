package com.example.EnterpriseRagCommunity.service.moderation.es;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.jpa.domain.Specification;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesReindexResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesSyncResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSamplesRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.ModerationSamplesAutoSyncConfigService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;

class ModerationSamplesSyncServiceTest {

    private ElasticsearchTemplate template;
    private ModerationSamplesIndexService indexService;
    private ModerationSamplesIndexConfigService indexConfigService;
    private ModerationSamplesRepository samplesRepository;
    private AiEmbeddingService embeddingService;
    private LlmGateway llmGateway;
    private LlmRoutingService llmRoutingService;
    private ModerationSimilarityConfigRepository configRepository;
    private ModerationSamplesAutoSyncConfigService autoSyncConfigService;
    private AppSettingsService appSettingsService;
    private IndexOperations indexOperations;
    private ModerationSamplesSyncService service;

    @BeforeEach
    void setUp() {
        template = mock(ElasticsearchTemplate.class);
        indexService = mock(ModerationSamplesIndexService.class);
        indexConfigService = mock(ModerationSamplesIndexConfigService.class);
        samplesRepository = mock(ModerationSamplesRepository.class);
        embeddingService = mock(AiEmbeddingService.class);
        llmGateway = mock(LlmGateway.class);
        llmRoutingService = mock(LlmRoutingService.class);
        configRepository = mock(ModerationSimilarityConfigRepository.class);
        appSettingsService = mock(AppSettingsService.class);
        autoSyncConfigService = new ModerationSamplesAutoSyncConfigService(appSettingsService);
        indexOperations = mock(IndexOperations.class);

        when(indexService.getIndexName()).thenReturn("moderation_es");
        when(indexConfigService.getEmbeddingModelOrDefault()).thenReturn(null);
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(0);
        when(template.indexOps(any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(indexOperations);
        when(llmRoutingService.listEnabledTargets(LlmQueueTaskType.SIMILARITY_EMBEDDING)).thenReturn(List.of());
        when(configRepository.findAll()).thenReturn(List.of());

        service = new ModerationSamplesSyncService(
                template,
                indexService,
                indexConfigService,
                samplesRepository,
                embeddingService,
                llmGateway,
                llmRoutingService,
                configRepository,
                autoSyncConfigService
        );
    }

    @Test
    void upsertById_shouldRejectInvalidId() {
        ModerationSamplesSyncResult r1 = service.upsertById(null);
        ModerationSamplesSyncResult r2 = service.upsertById(0L);

        assertThat(r1.isSuccess()).isFalse();
        assertThat(r2.isSuccess()).isFalse();
        assertThat(r1.getMessage()).isEqualTo("id is required");
    }

    @Test
    void upsertById_shouldHandleLoadFailuresAndNotFound() {
        when(samplesRepository.findById(1L)).thenThrow(new RuntimeException("db err"));
        ModerationSamplesSyncResult failLoad = service.upsertById(1L);
        assertThat(failLoad.isSuccess()).isFalse();
        assertThat(failLoad.getMessage()).contains("load sample failed");

        when(samplesRepository.findById(2L)).thenReturn(Optional.empty());
        ModerationSamplesSyncResult notFound = service.upsertById(2L);
        assertThat(notFound.isSuccess()).isFalse();
        assertThat(notFound.getMessage()).contains("sample not found");
    }

    @Test
    void upsertById_shouldUseGatewayAndFailOnEmptyVector() throws Exception {
        ModerationSamplesEntity e = sample(10L, "hello");
        when(samplesRepository.findById(10L)).thenReturn(Optional.of(e));
        when(llmGateway.embedOnceRouted(any(), any(), any(), anyString()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[0], 0, "m"));

        ModerationSamplesSyncResult out = service.upsertById(10L);

        assertThat(out.isSuccess()).isFalse();
        assertThat(out.getMessage()).contains("empty vector");
    }

    @Test
    void upsertById_shouldFailOnDimsMismatch() throws Exception {
        ModerationSamplesEntity e = sample(11L, "hello");
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(3);
        when(samplesRepository.findById(11L)).thenReturn(Optional.of(e));
        when(llmGateway.embedOnceRouted(any(), any(), any(), anyString()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f}, 2, "m"));

        ModerationSamplesSyncResult out = service.upsertById(11L);

        assertThat(out.isSuccess()).isFalse();
        assertThat(out.getMessage()).contains("dims mismatch");
    }

    @Test
    void upsertById_shouldUseEmbeddingServiceWhenConfiguredModelEnabled() throws Exception {
        ModerationSamplesEntity e = sample(12L, "abcdef");
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setMaxInputChars(3);
        when(configRepository.findAll()).thenReturn(List.of(cfg));
        when(indexConfigService.getEmbeddingModelOrDefault()).thenReturn("model-a");
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(2);
        when(samplesRepository.findById(12L)).thenReturn(Optional.of(e));
        when(llmRoutingService.listEnabledTargets(LlmQueueTaskType.SIMILARITY_EMBEDDING))
                .thenReturn(List.of(new LlmRoutingService.RouteTarget(
                        new LlmRoutingService.TargetId("p1", "model-a"), 1, 1, null
                )));
        when(embeddingService.embedOnceForTask(anyString(), anyString(), any(), any()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f}, 2, "model-a"));
        doThrow(new RuntimeException("refresh err")).when(indexOperations).refresh();

        ModerationSamplesSyncResult out = service.upsertById(12L);

        assertThat(out.isSuccess()).isTrue();
        verify(embeddingService).embedOnceForTask("abc", "model-a", null, LlmQueueTaskType.SIMILARITY_EMBEDDING);
        verify(indexService).ensureIndex(2);
        verify(template).save(any(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class));
        verify(indexOperations).refresh();
    }

    @Test
    void upsertById_shouldReturnFailureWhenSaveThrows() throws Exception {
        ModerationSamplesEntity e = sample(13L, "t");
        when(samplesRepository.findById(13L)).thenReturn(Optional.of(e));
        when(llmGateway.embedOnceRouted(any(), any(), any(), anyString()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f}, 1, "x"));
        doThrow(new RuntimeException("save fail")).when(template)
                .save(any(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class));

        ModerationSamplesSyncResult out = service.upsertById(13L);

        assertThat(out.isSuccess()).isFalse();
        assertThat(out.getMessage()).contains("save fail");
    }

    @Test
    void upsertById_shouldFallbackToGatewayWhenConfiguredModelNotEnabled() throws Exception {
        ModerationSamplesEntity e = sample(14L, "hello");
        when(indexConfigService.getEmbeddingModelOrDefault()).thenReturn("model-b");
        when(samplesRepository.findById(14L)).thenReturn(Optional.of(e));
        when(llmRoutingService.listEnabledTargets(LlmQueueTaskType.SIMILARITY_EMBEDDING))
                .thenReturn(List.of(new LlmRoutingService.RouteTarget(
                        new LlmRoutingService.TargetId("p1", "other-model"), 1, 1, null
                )));
        when(llmGateway.embedOnceRouted(any(), any(), any(), anyString()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f}, 2, "routed"));

        ModerationSamplesSyncResult out = service.upsertById(14L);

        assertThat(out.isSuccess()).isTrue();
        verify(llmGateway).embedOnceRouted(eq(LlmQueueTaskType.SIMILARITY_EMBEDDING), eq(null), eq(null), anyString());
        verify(embeddingService, never()).embedOnceForTask(anyString(), anyString(), any(), any());
    }

    @Test
    void upsertById_shouldUseConfiguredModelWithMixedTargets() throws Exception {
        ModerationSamplesEntity e = sample(16L, "abcdef");
        when(indexConfigService.getEmbeddingModelOrDefault()).thenReturn("model-a");
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(2);
        when(samplesRepository.findById(16L)).thenReturn(Optional.of(e));
        when(llmRoutingService.listEnabledTargets(LlmQueueTaskType.SIMILARITY_EMBEDDING))
                .thenReturn(List.of(
                        new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p1", "other"), 1, 1, null),
                        new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p1", "model-a"), 1, 1, null)
                ));
        when(embeddingService.embedOnceForTask(anyString(), anyString(), any(), any()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f}, 2, "model-a"));

        ModerationSamplesSyncResult out = service.upsertById(16L);

        assertThat(out.isSuccess()).isTrue();
        verify(embeddingService).embedOnceForTask(anyString(), eq("model-a"), any(), eq(LlmQueueTaskType.SIMILARITY_EMBEDDING));
    }

    @Test
    void upsertById_shouldIgnoreNullTargetIdWhenMatchingConfiguredModel() throws Exception {
        ModerationSamplesEntity e = sample(18L, "abcdef");
        when(indexConfigService.getEmbeddingModelOrDefault()).thenReturn("model-a");
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(2);
        when(samplesRepository.findById(18L)).thenReturn(Optional.of(e));
        when(llmRoutingService.listEnabledTargets(LlmQueueTaskType.SIMILARITY_EMBEDDING))
                .thenReturn(List.of(
                        new LlmRoutingService.RouteTarget(null, 1, 1, null),
                        new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p1", "model-a"), 1, 1, null)
                ));
        when(embeddingService.embedOnceForTask(anyString(), anyString(), any(), any()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f}, 2, "model-a"));

        ModerationSamplesSyncResult out = service.upsertById(18L);

        assertThat(out.isSuccess()).isTrue();
        verify(embeddingService).embedOnceForTask(anyString(), eq("model-a"), any(), eq(LlmQueueTaskType.SIMILARITY_EMBEDDING));
    }

    @Test
    void upsertById_shouldHandleConfigLoadExceptionAndStillSucceed() throws Exception {
        ModerationSamplesEntity e = sample(15L, "abcdef");
        when(configRepository.findAll()).thenThrow(new RuntimeException("cfg err"));
        when(samplesRepository.findById(15L)).thenReturn(Optional.of(e));
        when(llmGateway.embedOnceRouted(any(), any(), any(), anyString()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f, 3f}, 3, "routed"));

        ModerationSamplesSyncResult out = service.upsertById(15L);

        assertThat(out.isSuccess()).isTrue();
        verify(indexService).ensureIndex(3);
    }

    @Test
    void upsertById_shouldSkipDimsValidationWhenExpectedDimsNotPositive() throws Exception {
        ModerationSamplesEntity e = sample(17L, "abcdef");
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(0);
        when(samplesRepository.findById(17L)).thenReturn(Optional.of(e));
        when(llmGateway.embedOnceRouted(any(), any(), any(), anyString()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{1f, 2f, 3f}, 3, "routed"));

        ModerationSamplesSyncResult out = service.upsertById(17L);

        assertThat(out.isSuccess()).isTrue();
    }

    @Test
    void deleteById_shouldCoverInvalidSuccessAndFailure() {
        ModerationSamplesSyncResult invalid = service.deleteById(0L);
        assertThat(invalid.isSuccess()).isFalse();
        assertThat(invalid.getMessage()).isEqualTo("id is required");

        doThrow(new RuntimeException("refresh err")).when(indexOperations).refresh();
        ModerationSamplesSyncResult ok = service.deleteById(9L);
        assertThat(ok.isSuccess()).isTrue();

        doThrow(new RuntimeException("es err")).when(template)
                .delete(anyString(), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class));
        assertThatThrownBy(() -> service.deleteById(10L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteById_shouldSucceedWhenRefreshSucceeds() {
        ModerationSamplesSyncResult out = service.deleteById(20L);
        assertThat(out.isSuccess()).isTrue();
        verify(indexOperations).refresh();
    }

    @Test
    void deleteById_shouldRejectNullId() {
        ModerationSamplesSyncResult out = service.deleteById(null);
        assertThat(out.isSuccess()).isFalse();
        assertThat(out.getMessage()).isEqualTo("id is required");
    }

    @Test
    void syncIncremental_shouldUseCursorAndMarkFinished() {
        when(appSettingsService.getLongOrDefault(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, 0L)).thenReturn(15L);
        when(samplesRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty())
                .thenReturn(Page.empty());

        ModerationSamplesReindexResponse resp = service.syncIncremental(true, 200, null);

        assertThat(resp.getFromId()).isEqualTo(15L);
        verify(appSettingsService).upsertString(eq(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID), anyString());
        verify(appSettingsService).upsertString(eq(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_AT), anyString());
    }

    @Test
    void syncIncremental_shouldRespectProvidedFromIdWithoutCursorLookup() {
        when(samplesRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty())
                .thenReturn(Page.empty());

        ModerationSamplesReindexResponse resp = service.syncIncremental(false, 0, 9L);

        assertThat(resp.getFromId()).isEqualTo(9L);
        assertThat(resp.getBatchSize()).isEqualTo(1);
        assertThat(resp.getOnlyEnabled()).isFalse();
        verify(appSettingsService, never()).getLongOrDefault(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, 0L);
    }

    @Test
    void syncIncremental_shouldUseNullFromIdWhenCursorNotPositive() {
        when(appSettingsService.getLongOrDefault(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, 0L)).thenReturn(0L);
        when(samplesRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());

        ModerationSamplesReindexResponse resp = service.syncIncremental(null, null, null);

        assertThat(resp.getFromId()).isNull();
        assertThat(resp.getBatchSize()).isEqualTo(200);
        assertThat(resp.getOnlyEnabled()).isTrue();
    }

    @Test
    void reindexAll_shouldAbortWhenClearFails() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.delete()).thenReturn(false);

        ModerationSamplesReindexResponse resp = service.reindexAll(true, 20, null);

        assertThat(resp.getCleared()).isFalse();
        assertThat(resp.getClearError()).contains("failed to delete ES index");
        assertThat(resp.getTotal()).isZero();
    }

    @Test
    void reindexAll_shouldCapFailedIdsTo50() {
        ModerationSamplesSyncService spyService = spy(service);
        ModerationSamplesSyncResult fail = new ModerationSamplesSyncResult();
        fail.setSuccess(false);
        fail.setMessage("x");
        doReturn(fail).when(spyService).upsertById(anyLong());

        List<ModerationSamplesEntity> batch = new ArrayList<>();
        for (long i = 1; i <= 55; i++) {
            batch.add(sample(i, "n" + i));
        }
        Page<ModerationSamplesEntity> p1 = new PageImpl<>(batch);
        Page<ModerationSamplesEntity> p2 = new PageImpl<>(List.of());
        when(samplesRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(p1, p2);

        ModerationSamplesReindexResponse resp = spyService.reindexAll(true, 100, 1L);

        assertThat(resp.getFailed()).isEqualTo(55);
        assertThat(resp.getFailedIds()).hasSize(50);
        assertThat(resp.getLastId()).isEqualTo(55L);
        verify(appSettingsService).upsertString(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, "55");
    }

    @Test
    void reindexAll_shouldClampBatchSizeAndKeepOnlyEnabledDefaultTrue() {
        when(samplesRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());

        ModerationSamplesReindexResponse resp = service.reindexAll(null, 5000, 5L);

        assertThat(resp.getBatchSize()).isEqualTo(1000);
        assertThat(resp.getOnlyEnabled()).isTrue();
        assertThat(resp.getFromId()).isEqualTo(5L);
    }

    @Test
    void reindexAll_shouldClearWhenIndexMissing() {
        when(indexOperations.exists()).thenReturn(false);
        when(samplesRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());
        SearchHits empty = mock(SearchHits.class);
        when(empty.isEmpty()).thenReturn(true);
        when(template.search(any(org.springframework.data.elasticsearch.core.query.CriteriaQuery.class), eq(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(empty);

        ModerationSamplesReindexResponse resp = service.reindexAll(true, 10, null);

        assertThat(resp.getCleared()).isTrue();
        verify(indexService).ensureIndex();
    }

    @Test
    void reindexAll_shouldClearWhenIndexExistsAndDeleteSucceeds() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.delete()).thenReturn(true);
        when(samplesRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());
        SearchHits empty = mock(SearchHits.class);
        when(empty.isEmpty()).thenReturn(true);
        when(template.search(any(org.springframework.data.elasticsearch.core.query.CriteriaQuery.class), eq(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(empty);

        ModerationSamplesReindexResponse resp = service.reindexAll(true, 10, null);

        assertThat(resp.getCleared()).isTrue();
        assertThat(resp.getClearError()).isNull();
    }

    @Test
    void reindexAll_shouldHandleNullSampleIdAndKeepLastId() {
        ModerationSamplesSyncService spyService = spy(service);
        ModerationSamplesSyncResult fail = new ModerationSamplesSyncResult();
        fail.setSuccess(false);
        fail.setMessage("id is required");
        doReturn(fail).when(spyService).upsertById(any());

        ModerationSamplesEntity e = sample(1L, "x");
        e.setId(null);
        when(samplesRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(e)), Page.empty());

        ModerationSamplesReindexResponse resp = spyService.reindexAll(true, 10, 1L);

        assertThat(resp.getFailed()).isEqualTo(1);
        assertThat(resp.getFailedIds()).isEmpty();
        assertThat(resp.getLastId()).isEqualTo(1L);
    }

    @Test
    void reindexAll_shouldExecuteSpecificationLambdaBranches() {
        doAnswer(inv -> {
            Specification<ModerationSamplesEntity> spec = inv.getArgument(0);
            invokeSpec(spec);
            return Page.empty();
        }).when(samplesRepository).findAll(any(Specification.class), any(PageRequest.class));

        service.reindexAll(true, 10, 2L);
        service.reindexAll(false, 10, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invokeSpec(Specification<ModerationSamplesEntity> spec) {
        Root root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate p0 = mock(Predicate.class);
        Predicate p1 = mock(Predicate.class);
        Predicate p2 = mock(Predicate.class);
        Predicate p3 = mock(Predicate.class);
        Predicate p4 = mock(Predicate.class);
        Path enabledPath = mock(Path.class);
        Path idPath = mock(Path.class);
        when(cb.conjunction()).thenReturn(p0);
        when(root.get("enabled")).thenReturn(enabledPath);
        when(root.get("id")).thenReturn(idPath);
        when(cb.equal(enabledPath, true)).thenReturn(p1);
        when(cb.greaterThan(idPath, 0L)).thenReturn(p2);
        when(cb.and(p0, p1)).thenReturn(p3);
        when(cb.and(p3, p2)).thenReturn(p4);
        when(cb.and(p0, p2)).thenReturn(p4);
        spec.toPredicate(root, query, cb);
    }

    @Test
    void cleanupOrphans_shouldSkipInvalidOrNonPositiveIds() throws Exception {
        Method m = ModerationSamplesSyncService.class.getDeclaredMethod("cleanupOrphans");
        m.setAccessible(true);
        when(indexOperations.exists()).thenReturn(true);

        SearchHit<Document> h1 = mock(SearchHit.class);
        SearchHit<Document> h2 = mock(SearchHit.class);
        Document d1 = Document.create();
        d1.put("id", "bad");
        Document d2 = Document.create();
        d2.put("id", 0L);
        when(h1.getContent()).thenReturn(d1);
        when(h2.getContent()).thenReturn(d2);

        SearchHits<Document> hits1 = mock(SearchHits.class);
        when(hits1.isEmpty()).thenReturn(false);
        when(hits1.iterator()).thenReturn(List.of(h1, h2).iterator());
        when(hits1.getSearchHits()).thenReturn(List.of(h1, h2));

        when(template.search(any(org.springframework.data.elasticsearch.core.query.CriteriaQuery.class), eq(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(hits1);

        Object stat = m.invoke(service);
        Class<?> clz = stat.getClass();
        var deleted = clz.getDeclaredField("deleted");
        var failed = clz.getDeclaredField("failed");
        deleted.setAccessible(true);
        failed.setAccessible(true);
        assertThat(deleted.get(stat)).isEqualTo(0L);
        assertThat(failed.get(stat)).isEqualTo(0L);
    }

    @Test
    void cleanupOrphans_shouldDeleteSuccessAndIgnoreRefreshError() throws Exception {
        Method m = ModerationSamplesSyncService.class.getDeclaredMethod("cleanupOrphans");
        m.setAccessible(true);
        when(indexOperations.exists()).thenReturn(true);

        SearchHit<Document> h1 = mock(SearchHit.class);
        Document d1 = Document.create();
        d1.put("id", 300L);
        when(h1.getContent()).thenReturn(d1);

        SearchHits<Document> hits1 = mock(SearchHits.class);
        when(hits1.isEmpty()).thenReturn(false);
        when(hits1.iterator()).thenReturn(List.of(h1).iterator());
        when(hits1.getSearchHits()).thenReturn(List.of(h1));
        SearchHits<Document> hits2 = mock(SearchHits.class);
        when(hits2.isEmpty()).thenReturn(true);

        when(template.search(any(org.springframework.data.elasticsearch.core.query.CriteriaQuery.class), eq(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(hits1, hits2);
        when(samplesRepository.existsById(300L)).thenReturn(false);
        doThrow(new RuntimeException("refresh fail")).when(indexOperations).refresh();

        Object stat = m.invoke(service);
        Class<?> clz = stat.getClass();
        var deleted = clz.getDeclaredField("deleted");
        var failed = clz.getDeclaredField("failed");
        deleted.setAccessible(true);
        failed.setAccessible(true);
        assertThat(deleted.get(stat)).isEqualTo(1L);
        assertThat(failed.get(stat)).isEqualTo(0L);
    }

    @Test
    void cleanupOrphans_shouldReturnZeroWhenIndexMissing() throws Exception {
        Method m = ModerationSamplesSyncService.class.getDeclaredMethod("cleanupOrphans");
        m.setAccessible(true);
        when(indexOperations.exists()).thenReturn(false);

        Object stat = m.invoke(service);
        Class<?> clz = stat.getClass();
        var deleted = clz.getDeclaredField("deleted");
        var failed = clz.getDeclaredField("failed");
        deleted.setAccessible(true);
        failed.setAccessible(true);

        assertThat(deleted.get(stat)).isEqualTo(0L);
        assertThat(failed.get(stat)).isEqualTo(0L);
    }

    @Test
    void cleanupOrphans_shouldCoverDeleteSuccessAndFailure() throws Exception {
        Method m = ModerationSamplesSyncService.class.getDeclaredMethod("cleanupOrphans");
        m.setAccessible(true);

        when(indexOperations.exists()).thenReturn(true);

        SearchHit<Document> h1 = mock(SearchHit.class);
        SearchHit<Document> h2 = mock(SearchHit.class);
        Document d1 = Document.create();
        d1.put("id", 100L);
        Document d2 = Document.create();
        d2.put("id", 200L);
        when(h1.getContent()).thenReturn(d1);
        when(h2.getContent()).thenReturn(d2);

        SearchHits<Document> hits1 = mock(SearchHits.class);
        when(hits1.isEmpty()).thenReturn(false);
        when(hits1.iterator()).thenReturn(List.of(h1, h2).iterator());
        when(hits1.getSearchHits()).thenReturn(List.of(h1, h2));

        SearchHits<Document> hits2 = mock(SearchHits.class);
        when(hits2.isEmpty()).thenReturn(true);

        when(template.search(any(org.springframework.data.elasticsearch.core.query.CriteriaQuery.class), eq(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(hits1, hits2);
        when(samplesRepository.existsById(100L)).thenReturn(false);
        when(samplesRepository.existsById(200L)).thenThrow(new RuntimeException("db err"));
        doThrow(new RuntimeException("delete fail")).when(template)
                .delete(eq("100"), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class));

        Object stat = m.invoke(service);
        Class<?> clz = stat.getClass();
        var failed = clz.getDeclaredField("failed");
        var failedIds = clz.getDeclaredField("failedIds");
        failed.setAccessible(true);
        failedIds.setAccessible(true);

        assertThat(failed.get(stat)).isEqualTo(1L);
        assertThat((List<Long>) failedIds.get(stat)).containsExactly(100L);
    }

    @Test
    void cleanupOrphans_shouldCapFailedIdsAt50() throws Exception {
        Method m = ModerationSamplesSyncService.class.getDeclaredMethod("cleanupOrphans");
        m.setAccessible(true);
        when(indexOperations.exists()).thenReturn(true);

        List<SearchHit<Document>> hs = new ArrayList<>();
        for (long i = 1; i <= 55; i++) {
            SearchHit<Document> h = mock(SearchHit.class);
            Document d = Document.create();
            d.put("id", i);
            when(h.getContent()).thenReturn(d);
            hs.add(h);
            when(samplesRepository.existsById(i)).thenReturn(false);
        }
        SearchHits<Document> hits = mock(SearchHits.class);
        when(hits.isEmpty()).thenReturn(false);
        when(hits.iterator()).thenReturn(hs.iterator());
        when(hits.getSearchHits()).thenReturn(hs);
        SearchHits<Document> empty = mock(SearchHits.class);
        when(empty.isEmpty()).thenReturn(true);
        when(template.search(any(org.springframework.data.elasticsearch.core.query.CriteriaQuery.class), eq(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(hits, empty);
        doThrow(new RuntimeException("delete fail")).when(template)
                .delete(anyString(), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class));

        Object stat = m.invoke(service);
        Class<?> clz = stat.getClass();
        var failed = clz.getDeclaredField("failed");
        var failedIds = clz.getDeclaredField("failedIds");
        failed.setAccessible(true);
        failedIds.setAccessible(true);
        assertThat(failed.get(stat)).isEqualTo(55L);
        assertThat((List<Long>) failedIds.get(stat)).hasSize(50);
    }

    @Test
    void cleanupOrphans_shouldBreakWhenFirstSearchResultEmpty() throws Exception {
        Method m = ModerationSamplesSyncService.class.getDeclaredMethod("cleanupOrphans");
        m.setAccessible(true);
        when(indexOperations.exists()).thenReturn(true);
        SearchHits<Document> empty = mock(SearchHits.class);
        when(empty.isEmpty()).thenReturn(true);
        when(template.search(any(org.springframework.data.elasticsearch.core.query.CriteriaQuery.class), eq(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(empty);

        Object stat = m.invoke(service);
        Class<?> clz = stat.getClass();
        var deleted = clz.getDeclaredField("deleted");
        deleted.setAccessible(true);
        assertThat(deleted.get(stat)).isEqualTo(0L);
    }

    @Test
    void cleanupOrphans_shouldContinueWhenPageSizeReached() throws Exception {
        Method m = ModerationSamplesSyncService.class.getDeclaredMethod("cleanupOrphans");
        m.setAccessible(true);
        when(indexOperations.exists()).thenReturn(true);

        List<SearchHit<Document>> page = new ArrayList<>();
        for (long i = 1; i <= 200; i++) {
            SearchHit<Document> h = mock(SearchHit.class);
            Document d = Document.create();
            d.put("id", i);
            when(h.getContent()).thenReturn(d);
            page.add(h);
            when(samplesRepository.existsById(i)).thenReturn(true);
        }
        SearchHits<Document> full = mock(SearchHits.class);
        when(full.isEmpty()).thenReturn(false);
        when(full.iterator()).thenReturn(page.iterator());
        when(full.getSearchHits()).thenReturn(page);
        SearchHits<Document> empty = mock(SearchHits.class);
        when(empty.isEmpty()).thenReturn(true);
        when(template.search(any(org.springframework.data.elasticsearch.core.query.CriteriaQuery.class), eq(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class)))
                .thenReturn(full, empty);

        Object stat = m.invoke(service);
        Class<?> clz = stat.getClass();
        var deleted = clz.getDeclaredField("deleted");
        var failed = clz.getDeclaredField("failed");
        deleted.setAccessible(true);
        failed.setAccessible(true);
        assertThat(deleted.get(stat)).isEqualTo(0L);
        assertThat(failed.get(stat)).isEqualTo(0L);
    }

    @Test
    void privateHelpers_shouldCoverBranches() throws Exception {
        Method truncate = ModerationSamplesSyncService.class.getDeclaredMethod("truncateByChars", String.class, int.class);
        truncate.setAccessible(true);
        assertThat((String) truncate.invoke(null, null, 1)).isEqualTo("");
        assertThat((String) truncate.invoke(null, "abc", 0)).isEqualTo("abc");
        assertThat((String) truncate.invoke(null, "abc", 2)).isEqualTo("ab");
        assertThat((String) truncate.invoke(null, "abc", 3)).isEqualTo("abc");

        Method nonBlank = ModerationSamplesSyncService.class.getDeclaredMethod("toNonBlank", String.class);
        nonBlank.setAccessible(true);
        assertThat(nonBlank.invoke(null, (Object) null)).isNull();
        assertThat(nonBlank.invoke(null, "  ")).isNull();
        assertThat(nonBlank.invoke(null, " a ")).isEqualTo("a");

        Method toEsDoc = ModerationSamplesSyncService.class.getDeclaredMethod("toEsDoc", ModerationSamplesEntity.class, float[].class);
        toEsDoc.setAccessible(true);
        ModerationSamplesEntity e = sample(90L, "n");
        e.setRawText(null);
        e.setLabels(null);
        Document doc = (Document) toEsDoc.invoke(null, e, new float[]{1f, 2f});
        assertThat(doc.get("id")).isEqualTo(90L);
        assertThat(doc.get("normalized_text")).isEqualTo("n");
        assertThat(doc.get("embedding")).isNotNull();

        Document docNoEmb = (Document) toEsDoc.invoke(null, e, new float[0]);
        assertThat(docNoEmb).doesNotContainKey("embedding");

        ModerationSamplesEntity e2 = new ModerationSamplesEntity();
        Document docEmpty = (Document) toEsDoc.invoke(null, e2, null);
        assertThat(docEmpty).doesNotContainKey("id");
        assertThat(docEmpty).doesNotContainKey("category");
        assertThat(docEmpty).doesNotContainKey("created_at");

        ModerationSamplesEntity e3 = sample(91L, "h");
        e3.setCategory(ModerationSamplesEntity.Category.HISTORY_VIOLATION);
        Document docHistory = (Document) toEsDoc.invoke(null, e3, null);
        assertThat(docHistory.get("category")).isEqualTo("HISTORY_VIOLATION");

        ModerationSamplesEntity e4 = sample(92L, "k");
        e4.setLabels("spam");
        Document docWithLabels = (Document) toEsDoc.invoke(null, e4, null);
        assertThat(docWithLabels.get("labels")).isEqualTo("spam");
    }

    private static ModerationSamplesEntity sample(Long id, String normalized) {
        ModerationSamplesEntity e = new ModerationSamplesEntity();
        e.setId(id);
        e.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        e.setSource(ModerationSamplesEntity.Source.HUMAN);
        e.setRiskLevel(1);
        e.setEnabled(true);
        e.setRawText("raw");
        e.setNormalizedText(normalized);
        e.setTextHash("h");
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}
