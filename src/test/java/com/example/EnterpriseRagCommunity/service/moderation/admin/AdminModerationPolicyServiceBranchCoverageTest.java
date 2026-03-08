package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationPolicyConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminModerationPolicyServiceBranchCoverageTest {

    @Test
    void getConfigRejectsNullContentType() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        assertThrows(IllegalArgumentException.class, () -> service.getConfig(null));
    }

    @Test
    void getConfigThrowsWhenConfigNotInitialized() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        when(repository.findByContentType(ContentType.POST)).thenReturn(Optional.empty());
        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        assertThrows(IllegalStateException.class, () -> service.getConfig(ContentType.POST));
    }

    @Test
    void getConfigReturnsEmptyConfigWhenEntityConfigIsNull() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        ModerationPolicyConfigEntity entity = baseEntity(11L, ContentType.POST, "v1", null);
        when(repository.findByContentType(ContentType.POST)).thenReturn(Optional.of(entity));

        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        ModerationPolicyConfigDTO dto = service.getConfig(ContentType.POST);

        assertEquals(11L, dto.getId());
        assertEquals("v1", dto.getPolicyVersion());
        assertEquals(Map.of(), dto.getConfig());
        assertNotNull(dto.getUpdatedAt());
    }

    @Test
    void getConfigReturnsCopiedConfigMapForNonEmptyInput() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        ModerationPolicyConfigEntity entity = baseEntity(12L, ContentType.COMMENT, "v2", config);
        when(repository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(entity));

        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        ModerationPolicyConfigDTO dto = service.getConfig(ContentType.COMMENT);

        assertEquals(true, dto.getConfig().get("enabled"));
        config.put("enabled", false);
        assertEquals(true, dto.getConfig().get("enabled"));
        assertThrows(UnsupportedOperationException.class, () -> dto.getConfig().put("x", 1));
    }

    @Test
    void upsertRejectsNullPayload() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        assertThrows(IllegalArgumentException.class, () -> service.upsert(null, 1L, "alice"));
    }

    @Test
    void upsertRejectsMissingRequiredFields() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        ModerationPolicyConfigDTO missingContentType = new ModerationPolicyConfigDTO();
        assertThrows(IllegalArgumentException.class, () -> service.upsert(missingContentType, 1L, "alice"));

        ModerationPolicyConfigDTO missingVersion = new ModerationPolicyConfigDTO();
        missingVersion.setContentType(ContentType.POST);
        missingVersion.setConfig(Map.of("enabled", true));
        assertThrows(IllegalArgumentException.class, () -> service.upsert(missingVersion, 1L, "alice"));

        ModerationPolicyConfigDTO blankVersion = new ModerationPolicyConfigDTO();
        blankVersion.setContentType(ContentType.POST);
        blankVersion.setPolicyVersion("   ");
        blankVersion.setConfig(Map.of("enabled", true));
        assertThrows(IllegalArgumentException.class, () -> service.upsert(blankVersion, 1L, "alice"));

        ModerationPolicyConfigDTO missingConfig = new ModerationPolicyConfigDTO();
        missingConfig.setContentType(ContentType.POST);
        missingConfig.setPolicyVersion("v1");
        assertThrows(IllegalArgumentException.class, () -> service.upsert(missingConfig, 1L, "alice"));
    }

    @Test
    void upsertThrowsWhenBaselineConfigMissing() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        when(repository.findByContentType(ContentType.PROFILE)).thenReturn(Optional.empty());
        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        ModerationPolicyConfigDTO payload = new ModerationPolicyConfigDTO();
        payload.setContentType(ContentType.PROFILE);
        payload.setPolicyVersion("v1");
        payload.setConfig(Map.of("mode", "strict"));

        assertThrows(IllegalStateException.class, () -> service.upsert(payload, 2L, "bob"));
    }

    @Test
    void upsertUpdatesExistingEntityAndWritesAudit() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, auditLogWriter, auditDiffBuilder);

        ModerationPolicyConfigEntity existing = baseEntity(21L, ContentType.POST, "v-old", Map.of("enabled", false));
        when(repository.findByContentType(ContentType.POST)).thenReturn(Optional.of(existing), Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of("changed", true));

        Map<String, Object> payloadConfig = new LinkedHashMap<>();
        payloadConfig.put("enabled", true);
        payloadConfig.put("level", "high");

        ModerationPolicyConfigDTO payload = new ModerationPolicyConfigDTO();
        payload.setContentType(ContentType.POST);
        payload.setPolicyVersion("  v-new  ");
        payload.setConfig(payloadConfig);

        ModerationPolicyConfigDTO out = service.upsert(payload, 100L, "alice");

        assertEquals("v-new", out.getPolicyVersion());
        assertEquals(true, out.getConfig().get("enabled"));
        assertEquals("high", out.getConfig().get("level"));
        assertEquals("alice", out.getUpdatedBy());
        assertNotNull(out.getUpdatedAt());
        verify(auditLogWriter).write(
                eq(100L),
                eq("alice"),
                eq("CONFIG_CHANGE"),
                eq("MODERATION_POLICY_CONFIG"),
                eq(21L),
                any(),
                eq("更新审核策略配置"),
                eq(null),
                eq(Map.of("changed", true))
        );
    }

    @Test
    void upsertCreatesNewEntityWhenSecondLookupMissesAndNormalizesEmptyConfig() {
        ModerationPolicyConfigRepository repository = mock(ModerationPolicyConfigRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdminModerationPolicyService service = new AdminModerationPolicyService(repository, auditLogWriter, auditDiffBuilder);

        ModerationPolicyConfigEntity baseline = baseEntity(31L, ContentType.COMMENT, "v-base", Map.of("x", 1));
        when(repository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(baseline), Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            ModerationPolicyConfigEntity saved = invocation.getArgument(0);
            saved.setId(32L);
            return saved;
        });
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        ModerationPolicyConfigDTO payload = new ModerationPolicyConfigDTO();
        payload.setContentType(ContentType.COMMENT);
        payload.setPolicyVersion("v-created");
        payload.setConfig(Map.of());

        ModerationPolicyConfigDTO out = service.upsert(payload, null, "operator");
        assertEquals(32L, out.getId());
        assertEquals(Map.of(), out.getConfig());
        assertEquals("operator", out.getUpdatedBy());
        verify(auditLogWriter).write(
                eq(null),
                eq("operator"),
                eq("CONFIG_CHANGE"),
                eq("MODERATION_POLICY_CONFIG"),
                eq(32L),
                any(),
                eq("更新审核策略配置"),
                eq(null),
                eq(Map.of())
        );
        assertTrue(out.getConfig().isEmpty());
    }

    private static ModerationPolicyConfigEntity baseEntity(Long id, ContentType contentType, String policyVersion, Map<String, Object> config) {
        ModerationPolicyConfigEntity entity = new ModerationPolicyConfigEntity();
        entity.setId(id);
        entity.setVersion(1);
        entity.setContentType(contentType);
        entity.setPolicyVersion(policyVersion);
        entity.setConfig(config);
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedBy(1L);
        return entity;
    }
}
