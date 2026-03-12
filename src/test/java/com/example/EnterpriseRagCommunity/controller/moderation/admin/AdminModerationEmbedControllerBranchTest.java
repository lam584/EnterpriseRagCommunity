package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSampleCreateRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSampleUpdateRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesIndexStatusResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesReindexResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesSyncResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarHitsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSamplesRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarHitsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSimilarityService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.ModerationSamplesAutoSyncConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesSyncService;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@ExtendWith(MockitoExtension.class)
class AdminModerationEmbedControllerBranchTest {

    @Mock
    private ModerationSimilarityService similarityService;
    @Mock
    private ModerationSimilarityConfigRepository configRepository;
    @Mock
    private ModerationSamplesIndexConfigService indexConfigService;
    @Mock
    private ModerationSamplesRepository samplesRepository;
    @Mock
    private ModerationSimilarHitsRepository hitsRepository;
    @Mock
    private ModerationSamplesSyncService samplesSyncService;
    @Mock
    private ModerationSamplesIndexService samplesIndexService;
    @Mock
    private ModerationSamplesAutoSyncConfigService samplesAutoSyncConfigService;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private AuditDiffBuilder auditDiffBuilder;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getConfig_createsDefaultWhenRepositoryEmpty() {
        AdminModerationEmbedController controller = newController();
        when(configRepository.findAll()).thenReturn(List.of());
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(1024);
        when(indexConfigService.getDefaultTopKOrDefault()).thenReturn(12);
        when(indexConfigService.getDefaultThresholdOrDefault()).thenReturn(0.88);
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.getConfig();

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().getEnabled());
        assertEquals(1024, resp.getBody().getEmbeddingDims());
        assertEquals(12, resp.getBody().getDefaultTopK());
        assertEquals(0.88, resp.getBody().getDefaultThreshold());
    }

    @Test
    void getConfig_returnsExistingConfig() {
        AdminModerationEmbedController controller = newController();
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setId(9L);
        cfg.setEnabled(true);
        when(configRepository.findAll()).thenReturn(List.of(cfg));

        var resp = controller.getConfig();

        assertEquals(200, resp.getStatusCode().value());
        assertSame(cfg, resp.getBody());
    }

    @Test
    void updateConfig_throwsWhenEnabledMissing() {
        AdminModerationEmbedController controller = newController();
        ModerationSimilarityConfigEntity payload = new ModerationSimilarityConfigEntity();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> controller.updateConfig(payload));

        assertEquals("enabled 不能为空", ex.getMessage());
    }

    @Test
    void updateConfig_throwsWhenPayloadIsNull() {
        AdminModerationEmbedController controller = newController();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> controller.updateConfig(null));

        assertEquals("enabled 不能为空", ex.getMessage());
    }

    @Test
    void updateConfig_clampsValuesAndWritesAudit() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(" alice@example.com ", "N/A", List.of())
        );
        AdminModerationEmbedController controller = newController();
        ModerationSimilarityConfigEntity current = new ModerationSimilarityConfigEntity();
        current.setId(1L);
        current.setEnabled(true);
        current.setDefaultTopK(10);
        current.setDefaultThreshold(0.2);
        when(configRepository.findAll()).thenReturn(List.of(current));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of("changed", true));

        ModerationSimilarityConfigEntity payload = new ModerationSimilarityConfigEntity();
        payload.setEnabled(false);
        payload.setEmbeddingModel("   ");
        payload.setEmbeddingDims(-1);
        payload.setMaxInputChars(-2);
        payload.setDefaultTopK(100);
        payload.setDefaultThreshold(2.5);
        payload.setDefaultNumCandidates(-3);

        var resp = controller.updateConfig(payload);

        assertEquals(200, resp.getStatusCode().value());
        ModerationSimilarityConfigEntity saved = resp.getBody();
        assertFalse(saved.getEnabled());
        assertNull(saved.getEmbeddingModel());
        assertEquals(0, saved.getEmbeddingDims());
        assertEquals(0, saved.getMaxInputChars());
        assertEquals(50, saved.getDefaultTopK());
        assertEquals(1.0, saved.getDefaultThreshold());
        assertEquals(0, saved.getDefaultNumCandidates());
        verify(auditLogWriter).write(eq(null), eq("alice@example.com"), eq("CONFIG_CHANGE"), eq("MODERATION_EMBED_CONFIG"),
                eq(1L), any(), eq("更新嵌入相似检测配置"), eq(null), any());
    }

    @Test
    void updateConfig_keepsExistingWhenOptionalFieldsMissing() {
        AdminModerationEmbedController controller = newController();
        ModerationSimilarityConfigEntity current = new ModerationSimilarityConfigEntity();
        current.setId(2L);
        current.setEnabled(false);
        current.setEmbeddingModel("m1");
        current.setEmbeddingDims(1536);
        current.setMaxInputChars(888);
        current.setDefaultTopK(12);
        current.setDefaultThreshold(0.4);
        current.setDefaultNumCandidates(99);
        when(configRepository.findAll()).thenReturn(List.of(current));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        ModerationSimilarityConfigEntity payload = new ModerationSimilarityConfigEntity();
        payload.setEnabled(true);
        var resp = controller.updateConfig(payload);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(true, resp.getBody().getEnabled());
        assertEquals("m1", resp.getBody().getEmbeddingModel());
        assertEquals(1536, resp.getBody().getEmbeddingDims());
        assertEquals(888, resp.getBody().getMaxInputChars());
        assertEquals(12, resp.getBody().getDefaultTopK());
        assertEquals(0.4, resp.getBody().getDefaultThreshold());
        assertEquals(99, resp.getBody().getDefaultNumCandidates());
    }

    @Test
    void updateConfig_handlesLowBoundsAndNonBlankModel() {
        AdminModerationEmbedController controller = newController();
        ModerationSimilarityConfigEntity current = new ModerationSimilarityConfigEntity();
        current.setId(22L);
        current.setEnabled(true);
        when(configRepository.findAll()).thenReturn(List.of(current));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        ModerationSimilarityConfigEntity payload = new ModerationSimilarityConfigEntity();
        payload.setEnabled(true);
        payload.setEmbeddingModel("  model-x  ");
        payload.setDefaultTopK(0);
        payload.setDefaultThreshold(-0.2);
        var resp = controller.updateConfig(payload);

        assertEquals("model-x", resp.getBody().getEmbeddingModel());
        assertEquals(1, resp.getBody().getDefaultTopK());
        assertEquals(0.0, resp.getBody().getDefaultThreshold());
    }

    @Test
    void getIndexStatus_returnsUnavailableWhenIndexNotExists() {
        AdminModerationEmbedController controller = newController();
        when(samplesIndexService.getIndexName()).thenReturn("idx");
        when(samplesIndexService.indexExists()).thenReturn(false);
        when(configRepository.findAll()).thenReturn(List.of());
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(1536);
        when(samplesAutoSyncConfigService.getLastIncrementalSyncAt()).thenReturn(Optional.of("2026-03-11T10:00:00"));

        var resp = controller.getIndexStatus();
        ModerationSamplesIndexStatusResponse body = resp.getBody();

        assertEquals(200, resp.getStatusCode().value());
        assertFalse(body.getAvailable());
        assertEquals("索引不存在", body.getAvailabilityMessage());
        assertEquals(1536, body.getEmbeddingDimsConfigured());
    }

    @Test
    void getIndexStatus_returnsUnavailableWhenMappingMissingOrMismatch() {
        AdminModerationEmbedController controller = newController();
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setEmbeddingDims(1024);
        when(samplesIndexService.getIndexName()).thenReturn("idx");
        when(samplesIndexService.indexExists()).thenReturn(true);
        when(samplesIndexService.countDocs()).thenReturn(99L);
        when(samplesAutoSyncConfigService.getLastIncrementalSyncAt()).thenReturn(Optional.empty());
        when(configRepository.findAll()).thenReturn(List.of(cfg));

        when(samplesIndexService.getEmbeddingDimsInMapping()).thenReturn(null);
        var r1 = controller.getIndexStatus().getBody();
        assertFalse(r1.getAvailable());
        assertEquals("索引映射缺少 embedding 向量字段", r1.getAvailabilityMessage());

        when(samplesIndexService.getEmbeddingDimsInMapping()).thenReturn(768);
        var r2 = controller.getIndexStatus().getBody();
        assertFalse(r2.getAvailable());
        assertEquals("embedding 维度不一致（配置 1024 / 映射 768）", r2.getAvailabilityMessage());
    }

    @Test
    void getIndexStatus_returnsAvailableWhenDimsMatch() {
        AdminModerationEmbedController controller = newController();
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setEmbeddingDims(768);
        when(samplesIndexService.getIndexName()).thenReturn("idx");
        when(samplesIndexService.indexExists()).thenReturn(true);
        when(samplesIndexService.getEmbeddingDimsInMapping()).thenReturn(768);
        when(samplesIndexService.countDocs()).thenReturn(1L);
        when(samplesAutoSyncConfigService.getLastIncrementalSyncAt()).thenReturn(Optional.empty());
        when(configRepository.findAll()).thenReturn(List.of(cfg));

        var body = controller.getIndexStatus().getBody();

        assertTrue(body.getAvailable());
        assertNull(body.getAvailabilityMessage());
    }

    @Test
    void getIndexStatus_availableWhenConfiguredDimsInvalid() {
        AdminModerationEmbedController controller = newController();
        ModerationSimilarityConfigEntity cfg = new ModerationSimilarityConfigEntity();
        cfg.setEmbeddingDims(0);
        when(samplesIndexService.getIndexName()).thenReturn("idx");
        when(samplesIndexService.indexExists()).thenReturn(true);
        when(samplesIndexService.getEmbeddingDimsInMapping()).thenReturn(512);
        when(samplesIndexService.countDocs()).thenReturn(11L);
        when(samplesAutoSyncConfigService.getLastIncrementalSyncAt()).thenReturn(Optional.empty());
        when(configRepository.findAll()).thenReturn(List.of(cfg));

        var body = controller.getIndexStatus().getBody();

        assertTrue(body.getAvailable());
        assertNull(body.getAvailabilityMessage());
    }

    @Test
    void getIndexStatus_unavailableWhenMappingDimsIsZero() {
        AdminModerationEmbedController controller = newController();
        when(samplesIndexService.getIndexName()).thenReturn("idx");
        when(samplesIndexService.indexExists()).thenReturn(true);
        when(samplesIndexService.getEmbeddingDimsInMapping()).thenReturn(0);
        when(samplesIndexService.countDocs()).thenReturn(1L);
        when(samplesAutoSyncConfigService.getLastIncrementalSyncAt()).thenReturn(Optional.empty());
        when(configRepository.findAll()).thenReturn(List.of());
        when(indexConfigService.getEmbeddingDimsOrDefault()).thenReturn(512);

        var body = controller.getIndexStatus().getBody();

        assertFalse(body.getAvailable());
        assertEquals("索引映射缺少 embedding 向量字段", body.getAvailabilityMessage());
    }

    @Test
    void createSample_validatesRequiredFields() {
        AdminModerationEmbedController controller = newController();
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () -> controller.createSample(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ModerationSampleCreateRequest req = new ModerationSampleCreateRequest();
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () -> controller.createSample(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());

        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        req.setRawText("   ");
        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class, () -> controller.createSample(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex3.getStatusCode());
    }

    @Test
    void createSample_usesDefaultRiskLevel_whenRiskLevelMissing() {
        AdminModerationEmbedController controller = newController();
        ModerationSampleCreateRequest req = new ModerationSampleCreateRequest();
        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        req.setRawText("risk-text");
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.empty());
        when(samplesRepository.save(any())).thenAnswer(inv -> {
            ModerationSamplesEntity e = inv.getArgument(0);
            e.setId(150L);
            return e;
        });
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        ModerationSamplesSyncResult sync = new ModerationSamplesSyncResult();
        sync.setSuccess(true);
        when(samplesSyncService.upsertById(150L)).thenReturn(sync);

        var resp = controller.createSample(req);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, resp.getBody().getRiskLevel());
    }

    @Test
    void createSample_throwsConflictWhenDuplicateHashExists() {
        AdminModerationEmbedController controller = newController();
        ModerationSampleCreateRequest req = new ModerationSampleCreateRequest();
        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        req.setRawText("risk-text");
        ModerationSamplesEntity existing = new ModerationSamplesEntity();
        existing.setId(77L);
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.createSample(req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void createSample_returnsUnknownSyncWhenSyncThrows() {
        AdminModerationEmbedController controller = newController();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bob@example.com", "N/A", List.of())
        );
        ModerationSampleCreateRequest req = new ModerationSampleCreateRequest();
        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        req.setRawText("risk-text");
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.empty());
        when(samplesRepository.save(any())).thenAnswer(inv -> {
            ModerationSamplesEntity e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        doThrow(new RuntimeException("es down")).when(samplesSyncService).upsertById(101L);

        var resp = controller.createSample(req);

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertNull(resp.getBody().getEsSynced());
    }

    @Test
    void createSample_returnsSyncErrorMessageWhenSyncFailed() {
        AdminModerationEmbedController controller = newController();
        ModerationSampleCreateRequest req = new ModerationSampleCreateRequest();
        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        req.setRawText("risk-text");
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.empty());
        when(samplesRepository.save(any())).thenAnswer(inv -> {
            ModerationSamplesEntity e = inv.getArgument(0);
            e.setId(102L);
            return e;
        });
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        ModerationSamplesSyncResult sync = new ModerationSamplesSyncResult();
        sync.setSuccess(false);
        sync.setMessage(null);
        when(samplesSyncService.upsertById(102L)).thenReturn(sync);

        var resp = controller.createSample(req);

        assertEquals(false, resp.getBody().getEsSynced());
        assertEquals("ES sync failed", resp.getBody().getEsSyncMessage());
    }

    @Test
    void createSample_returnsSyncCustomErrorMessageWhenProvided() {
        AdminModerationEmbedController controller = newController();
        ModerationSampleCreateRequest req = new ModerationSampleCreateRequest();
        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        req.setRawText("risk-text");
        req.setRiskLevel(2);
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.empty());
        when(samplesRepository.save(any())).thenAnswer(inv -> {
            ModerationSamplesEntity e = inv.getArgument(0);
            e.setId(104L);
            return e;
        });
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        ModerationSamplesSyncResult sync = new ModerationSamplesSyncResult();
        sync.setSuccess(false);
        sync.setMessage("custom-sync-error");
        when(samplesSyncService.upsertById(104L)).thenReturn(sync);

        var resp = controller.createSample(req);

        assertEquals(false, resp.getBody().getEsSynced());
        assertEquals("custom-sync-error", resp.getBody().getEsSyncMessage());
    }

    @Test
    void createSample_returnsSyncedWhenSyncSuccess() {
        AdminModerationEmbedController controller = newController();
        ModerationSampleCreateRequest req = new ModerationSampleCreateRequest();
        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        req.setRawText("risk-text");
        req.setEnabled(false);
        req.setSource(ModerationSamplesEntity.Source.RULE);
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.empty());
        when(samplesRepository.save(any())).thenAnswer(inv -> {
            ModerationSamplesEntity e = inv.getArgument(0);
            e.setId(103L);
            return e;
        });
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        ModerationSamplesSyncResult sync = new ModerationSamplesSyncResult();
        sync.setSuccess(true);
        when(samplesSyncService.upsertById(103L)).thenReturn(sync);

        var resp = controller.createSample(req);

        assertEquals(true, resp.getBody().getEsSynced());
        assertNull(resp.getBody().getEsSyncMessage());
    }

    @Test
    void createSample_throwsConflictOnSaveDuplicate() {
        AdminModerationEmbedController controller = newController();
        ModerationSampleCreateRequest req = new ModerationSampleCreateRequest();
        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        req.setRawText("risk-text");
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.empty());
        when(samplesRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.createSample(req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateSample_validatesAndHandlesDuplicateHash() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesEntity current = sample(201L, "old", "hash-old");
        when(samplesRepository.findById(201L)).thenReturn(Optional.of(current));

        ModerationSampleUpdateRequest req1 = new ModerationSampleUpdateRequest();
        req1.setRawText("   ");
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () -> controller.updateSample(201L, req1));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ModerationSampleUpdateRequest req2 = new ModerationSampleUpdateRequest();
        req2.setRawText("new-text");
        ModerationSamplesEntity other = sample(999L, "x", "hash-new");
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.of(other));
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () -> controller.updateSample(201L, req2));
        assertEquals(HttpStatus.CONFLICT, ex2.getStatusCode());
    }

    @Test
    void updateSample_returnsSyncUnknownWhenSyncThrows() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesEntity current = sample(202L, "old", "hash-old");
        when(samplesRepository.findById(202L)).thenReturn(Optional.of(current));
        when(samplesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        doThrow(new RuntimeException("es down")).when(samplesSyncService).upsertById(202L);

        ModerationSampleUpdateRequest req = new ModerationSampleUpdateRequest();
        req.setRiskLevel(3);
        var resp = controller.updateSample(202L, req);

        assertEquals(200, resp.getStatusCode().value());
        assertNull(resp.getBody().getEsSynced());
    }

    @Test
    void updateSample_shouldHandleNullBodyAndNotFound() {
        AdminModerationEmbedController controller = newController();
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () -> controller.updateSample(99L, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        when(samplesRepository.findById(99L)).thenReturn(Optional.empty());
        ModerationSampleUpdateRequest req = new ModerationSampleUpdateRequest();
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () -> controller.updateSample(99L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex2.getStatusCode());
    }

    @Test
    void updateSample_shouldAllowDuplicateHashWhenSameId_andReturnFailedSyncMessage() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesEntity current = sample(203L, "old", "old-hash");
        when(samplesRepository.findById(203L)).thenReturn(Optional.of(current));
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.of(current));
        when(samplesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        ModerationSamplesSyncResult sync = new ModerationSamplesSyncResult();
        sync.setSuccess(false);
        sync.setMessage("sync-err");
        when(samplesSyncService.upsertById(203L)).thenReturn(sync);

        ModerationSampleUpdateRequest req = new ModerationSampleUpdateRequest();
        req.setRawText("new-text");
        req.setEnabled(false);
        req.setSource(ModerationSamplesEntity.Source.LLM);
        req.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        var resp = controller.updateSample(203L, req);

        assertEquals(false, resp.getBody().getEsSynced());
        assertEquals("sync-err", resp.getBody().getEsSyncMessage());
    }

    @Test
    void updateSample_shouldThrowConflictWhenSaveDuplicate() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesEntity current = sample(204L, "old", "h");
        when(samplesRepository.findById(204L)).thenReturn(Optional.of(current));
        when(samplesRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        ModerationSampleUpdateRequest req = new ModerationSampleUpdateRequest();
        req.setRiskLevel(1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.updateSample(204L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateSample_whenHashChangedAndNoDuplicate_shouldPersistNewHash() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesEntity current = sample(206L, "old", "old-hash");
        when(samplesRepository.findById(206L)).thenReturn(Optional.of(current));
        when(samplesRepository.findByTextHash(any())).thenReturn(Optional.empty());
        when(samplesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        ModerationSamplesSyncResult sync = new ModerationSamplesSyncResult();
        sync.setSuccess(false);
        sync.setMessage(null);
        when(samplesSyncService.upsertById(206L)).thenReturn(sync);

        ModerationSampleUpdateRequest req = new ModerationSampleUpdateRequest();
        req.setRawText("new-text");
        var resp = controller.updateSample(206L, req);

        assertEquals(false, resp.getBody().getEsSynced());
        assertEquals("ES sync failed", resp.getBody().getEsSyncMessage());
    }

    @Test
    void updateSample_shouldHandleSameHashAndOptionalFields() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesEntity current = sample(205L, "same-text", "same-hash");
        current.setTextHash(com.example.EnterpriseRagCommunity.service.moderation.ModerationSampleTextUtils.sha256Hex("same-text"));
        when(samplesRepository.findById(205L)).thenReturn(Optional.of(current));
        when(samplesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        ModerationSamplesSyncResult sync = new ModerationSamplesSyncResult();
        sync.setSuccess(true);
        when(samplesSyncService.upsertById(205L)).thenReturn(sync);

        ModerationSampleUpdateRequest req = new ModerationSampleUpdateRequest();
        req.setRawText("same-text");
        req.setLabels("[\"a\"]");
        req.setEnabled(true);
        req.setRiskLevel(5);
        var resp = controller.updateSample(205L, req);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(true, resp.getBody().getEsSynced());
        assertEquals("[\"a\"]", current.getLabels());
        assertEquals(5, current.getRiskLevel());
    }

    @Test
    void deleteSample_handlesBestEffortEsDelete() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesEntity current = sample(301L, "x", "h");
        when(samplesRepository.findById(301L)).thenReturn(Optional.of(current));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        doThrow(new RuntimeException("es delete failed")).when(samplesSyncService).deleteById(301L);

        var resp = controller.deleteSample(301L);

        assertEquals(204, resp.getStatusCode().value());
        verify(samplesRepository).deleteById(301L);
    }

    @Test
    void getSample_and_deleteSample_shouldThrowNotFound() {
        AdminModerationEmbedController controller = newController();
        when(samplesRepository.findById(666L)).thenReturn(Optional.empty());

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () -> controller.getSample(666L));
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () -> controller.deleteSample(666L));
        assertEquals(HttpStatus.NOT_FOUND, ex1.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, ex2.getStatusCode());
    }

    @Test
    void reindex_and_syncSamples_writeAudit() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesReindexResponse r = new ModerationSamplesReindexResponse();
        r.setTotal(10L);
        r.setSuccess(9L);
        r.setFailed(1L);
        when(samplesSyncService.reindexAll(true, 50, 2L)).thenReturn(r);
        when(samplesSyncService.syncIncremental(false, 20, 3L)).thenReturn(r);

        var a = controller.reindex(true, 50, 2L);
        var b = controller.syncSamples(false, 20, 3L);

        assertEquals(200, a.getStatusCode().value());
        assertEquals(200, b.getStatusCode().value());
        verify(auditLogWriter).write(eq(null), any(), eq("MODERATION_SAMPLES_REINDEX"), eq("MODERATION_SAMPLES_INDEX"),
                eq(null), any(), eq("重建样本索引"), eq(null), any());
        verify(auditLogWriter).write(eq(null), any(), eq("MODERATION_SAMPLES_SYNC"), eq("MODERATION_SAMPLES_INDEX"),
                eq(null), any(), eq("增量同步样本到索引"), eq(null), any());
    }

    @Test
    void syncOne_setsAuditResultBySyncStatus() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesSyncResult ok = new ModerationSamplesSyncResult();
        ok.setSuccess(true);
        ok.setMessage("ok");
        when(samplesSyncService.upsertById(901L)).thenReturn(ok);

        var resp = controller.syncOne(901L);

        assertEquals(200, resp.getStatusCode().value());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(eq(null), any(), eq("MODERATION_SAMPLE_SYNC"), eq("MODERATION_SAMPLE"),
                eq(901L), any(), eq("同步样本到索引"), eq(null), details.capture());
        assertEquals(true, details.getValue().get("success"));
        assertEquals("ok", details.getValue().get("message"));
    }

    @Test
    void syncOne_handlesNullAndFailedResult() {
        AdminModerationEmbedController controller = newController();
        when(samplesSyncService.upsertById(902L)).thenReturn(null);
        ModerationSamplesSyncResult failed = new ModerationSamplesSyncResult();
        failed.setSuccess(false);
        failed.setMessage("bad");
        when(samplesSyncService.upsertById(903L)).thenReturn(failed);

        var r1 = controller.syncOne(902L);
        var r2 = controller.syncOne(903L);

        assertNull(r1.getBody());
        assertSame(failed, r2.getBody());
    }

    @Test
    void check_and_sampleConfigEndpoints_shouldReturnData() {
        AdminModerationEmbedController controller = newController();
        when(similarityService.check(any())).thenReturn(new com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckResponse());
        ModerationSamplesAutoSyncConfigDTO cfg = new ModerationSamplesAutoSyncConfigDTO();
        when(samplesAutoSyncConfigService.getConfig()).thenReturn(cfg);
        when(samplesAutoSyncConfigService.updateConfig(any())).thenReturn(cfg);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        var checkResp = controller.check(new com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckRequest());
        var getCfgResp = controller.getSamplesAutoSyncConfig();
        var updateCfgResp = controller.updateSamplesAutoSyncConfig(new ModerationSamplesAutoSyncConfigDTO());

        assertEquals(200, checkResp.getStatusCode().value());
        assertEquals(200, getCfgResp.getStatusCode().value());
        assertEquals(200, updateCfgResp.getStatusCode().value());
    }

    @Test
    void reindex_and_syncSamples_acceptNullResult() {
        AdminModerationEmbedController controller = newController();
        when(samplesSyncService.reindexAll(null, null, null)).thenReturn(null);
        when(samplesSyncService.syncIncremental(null, null, null)).thenReturn(null);

        var a = controller.reindex(null, null, null);
        var b = controller.syncSamples(null, null, null);

        assertNull(a.getBody());
        assertNull(b.getBody());
    }

    @Test
    void privateHelpers_shouldCoverNullAndAuthBranches() {
        assertEquals(0, ((Map<?, ?>) ReflectionTestUtils.invokeMethod(AdminModerationEmbedController.class, "summarizeConfig", new Object[]{null})).size());
        assertEquals(0, ((Map<?, ?>) ReflectionTestUtils.invokeMethod(AdminModerationEmbedController.class, "summarizeSample", new Object[]{null})).size());
        SecurityContextHolder.clearContext();
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationEmbedController.class, "currentUsernameOrNull"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "n/a", List.of())
        );
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationEmbedController.class, "currentUsernameOrNull"));

        Authentication unauth = mock(Authentication.class);
        when(unauth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauth);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationEmbedController.class, "currentUsernameOrNull"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("  alice@example.com  ", "n/a", List.of())
        );
        assertEquals("alice@example.com",
                ReflectionTestUtils.invokeMethod(AdminModerationEmbedController.class, "currentUsernameOrNull"));

        Authentication broken = mock(Authentication.class);
        when(broken.isAuthenticated()).thenReturn(true);
        when(broken.getPrincipal()).thenThrow(new RuntimeException("x"));
        SecurityContextHolder.getContext().setAuthentication(broken);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationEmbedController.class, "currentUsernameOrNull"));

        ModerationSamplesEntity e = sample(10L, "x", "h");
        e.setRawText(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> summarized =
                (Map<String, Object>) ReflectionTestUtils.invokeMethod(AdminModerationEmbedController.class, "summarizeSample", e);
        assertEquals(0, summarized.get("rawTextLen"));
    }

    @Test
    void getSample_and_listSamples_shouldCoverSpecBranches() {
        AdminModerationEmbedController controller = newController();
        ModerationSamplesEntity e = sample(1L, "txt", "h1");
        when(samplesRepository.findById(1L)).thenReturn(Optional.of(e));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<ModerationSamplesEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(samplesRepository.findAll(specCaptor.capture(), any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of(e)));

        var getResp = controller.getSample(1L);
        assertEquals(200, getResp.getStatusCode().value());
        assertEquals(1L, getResp.getBody().getId());

        Page<ModerationSamplesEntity> p1 = controller.listSamples(1, 20, 1L,
                ModerationSamplesEntity.Category.AD_SAMPLE, true, " hash ", LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertEquals(1, p1.getTotalElements());
        executeSamplesSpecification(specCaptor.getValue());

        Page<ModerationSamplesEntity> p2 = controller.listSamples(0, 999, null,
                null, null, "   ", null, null);
        assertEquals(1, p2.getTotalElements());
        executeSamplesSpecification(specCaptor.getValue());

        Page<ModerationSamplesEntity> p3 = controller.listSamples(1, 20, null,
                null, null, null, null, null);
        assertEquals(1, p3.getTotalElements());
        executeSamplesSpecification(specCaptor.getValue());
    }

    @Test
    void listHits_shouldCoverSpecBranches() {
        AdminModerationEmbedController controller = newController();
        ModerationSimilarHitsEntity hit = new ModerationSimilarHitsEntity();
        hit.setId(3L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<ModerationSimilarHitsEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(hitsRepository.findAll(specCaptor.capture(), any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of(hit)));

        var page1 = controller.listHits(1, 20, ContentType.POST, 2L, 3L, 0.9, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertEquals(1, page1.getTotalElements());
        executeHitsSpecification(specCaptor.getValue());

        var page2 = controller.listHits(0, 999, null, null, null, null, null, null);
        assertEquals(1, page2.getTotalElements());
        executeHitsSpecification(specCaptor.getValue());
    }

    private static void executeSamplesSpecification(Specification<ModerationSamplesEntity> specification) {
        @SuppressWarnings("unchecked")
        Root<ModerationSamplesEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate p = mock(Predicate.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        lenient().when(cb.conjunction()).thenReturn(p);
        lenient().when(cb.and(nullable(Predicate.class), nullable(Predicate.class))).thenReturn(p);
        lenient().when(root.get(any(String.class))).thenReturn(path);
        lenient().when(cb.equal(any(), any())).thenReturn(p);
        lenient().when(cb.greaterThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(p);
        lenient().when(cb.lessThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(p);
        specification.toPredicate(root, query, cb);
    }

    private static void executeHitsSpecification(Specification<ModerationSimilarHitsEntity> specification) {
        @SuppressWarnings("unchecked")
        Root<ModerationSimilarHitsEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate p = mock(Predicate.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        lenient().when(cb.conjunction()).thenReturn(p);
        lenient().when(cb.and(nullable(Predicate.class), nullable(Predicate.class))).thenReturn(p);
        lenient().when(root.get(any(String.class))).thenReturn(path);
        lenient().when(cb.equal(any(), any())).thenReturn(p);
        lenient().when(cb.lessThanOrEqualTo(any(), any(Double.class))).thenReturn(p);
        lenient().when(cb.greaterThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(p);
        lenient().when(cb.lessThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(p);
        specification.toPredicate(root, query, cb);
    }

    private AdminModerationEmbedController newController() {
        return new AdminModerationEmbedController(
                similarityService,
                configRepository,
                indexConfigService,
                samplesRepository,
                hitsRepository,
                samplesSyncService,
                samplesIndexService,
                samplesAutoSyncConfigService,
                auditLogWriter,
                auditDiffBuilder
        );
    }

    private static ModerationSamplesEntity sample(Long id, String rawText, String textHash) {
        ModerationSamplesEntity e = new ModerationSamplesEntity();
        e.setId(id);
        e.setCategory(ModerationSamplesEntity.Category.AD_SAMPLE);
        e.setRawText(rawText);
        e.setNormalizedText(rawText);
        e.setTextHash(textHash);
        e.setRiskLevel(1);
        e.setSource(ModerationSamplesEntity.Source.HUMAN);
        e.setEnabled(true);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}
