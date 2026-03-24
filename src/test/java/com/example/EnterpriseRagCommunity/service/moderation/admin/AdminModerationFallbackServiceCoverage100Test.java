package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationConfidenceFallbackConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminModerationFallbackServiceCoverage100Test {

    @Test
    void upsertUpdatesAllFieldsAndWritesAudit() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, auditLogWriter, auditDiffBuilder);

        ModerationConfidenceFallbackConfigEntity existing = baseEntity();
        existing.setId(1L);
        existing.setThresholds(Map.of("before", 1));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of("changed", true));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
payload.setLlmEnabled(false);
        payload.setLlmRejectThreshold(0.9);
        payload.setLlmHumanThreshold(0.4);
        payload.setChunkLlmRejectThreshold(0.8);
        payload.setChunkLlmHumanThreshold(0.3);
        payload.setLlmTextRiskThreshold(0.6);
        payload.setLlmImageRiskThreshold(0.2);
        payload.setLlmStrongRejectThreshold(0.95);
        payload.setLlmStrongPassThreshold(0.1);
        payload.setLlmCrossModalThreshold(0.8);
        payload.setReportHumanThreshold(12);
        payload.setChunkThresholdChars(31000);
        payload.setThresholds(Map.of("custom.number", 123, "custom.bool", true));

        ModerationConfidenceFallbackConfigDTO out = svc.upsert(payload, 10L, "alice");
        assertEquals(false, out.getLlmEnabled());
        assertEquals(0.9, out.getLlmRejectThreshold());
        assertEquals(0.4, out.getLlmHumanThreshold());
        assertEquals(0.8, out.getChunkLlmRejectThreshold());
        assertEquals(0.3, out.getChunkLlmHumanThreshold());
        assertEquals(0.6, out.getLlmTextRiskThreshold());
        assertEquals(0.2, out.getLlmImageRiskThreshold());
        assertEquals(0.95, out.getLlmStrongRejectThreshold());
        assertEquals(0.1, out.getLlmStrongPassThreshold());
        assertEquals(0.8, out.getLlmCrossModalThreshold());
assertEquals(31000, out.getChunkThresholdChars());
        assertEquals(123, out.getThresholds().get("custom.number"));
        assertEquals(true, out.getThresholds().get("custom.bool"));
        assertNotNull(out.getUpdatedAt());
        assertEquals("alice", out.getUpdatedBy());

        verify(repo, times(1)).save(any(ModerationConfidenceFallbackConfigEntity.class));
        verify(auditLogWriter, times(1)).write(
                eq(10L),
                eq("alice"),
                eq("CONFIG_CHANGE"),
                eq("MODERATION_FALLBACK_CONFIG"),
                eq(1L),
                eq(AuditResult.SUCCESS),
                eq("更新置信回退机制配置"),
                eq(null),
                any()
        );
    }

    @Test
    void upsertRejectsAllRiskThresholdOutOfRange() {
        assertRejectsRange(p -> p.setLlmRejectThreshold(-0.1), "llmRejectThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmRejectThreshold(1.1), "llmRejectThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmHumanThreshold(-0.1), "llmHumanThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmHumanThreshold(1.1), "llmHumanThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setChunkLlmRejectThreshold(-0.1), "chunkLlmRejectThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setChunkLlmRejectThreshold(1.1), "chunkLlmRejectThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setChunkLlmHumanThreshold(-0.1), "chunkLlmHumanThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setChunkLlmHumanThreshold(1.1), "chunkLlmHumanThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmTextRiskThreshold(-0.1), "llmTextRiskThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmTextRiskThreshold(1.1), "llmTextRiskThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmImageRiskThreshold(-0.1), "llmImageRiskThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmImageRiskThreshold(1.1), "llmImageRiskThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmStrongRejectThreshold(-0.1), "llmStrongRejectThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmStrongRejectThreshold(1.1), "llmStrongRejectThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmStrongPassThreshold(-0.1), "llmStrongPassThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmStrongPassThreshold(1.1), "llmStrongPassThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmCrossModalThreshold(-0.1), "llmCrossModalThreshold must be within [0,1]");
        assertRejectsRange(p -> p.setLlmCrossModalThreshold(1.1), "llmCrossModalThreshold must be within [0,1]");
    }

    @Test
    void getConfigReturnsMappedDtoWithUpdatedByNull() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        ModerationConfidenceFallbackConfigEntity e = baseEntity();
        e.setId(21L);
        e.setVersion(7);
        e.setUpdatedAt(LocalDateTime.now());
        e.setThresholds(Map.of("chunk.global.enable", true));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(e));

        ModerationConfidenceFallbackConfigDTO dto = svc.getConfig();
        assertEquals(21L, dto.getId());
assertEquals(true, dto.getThresholds().get("chunk.global.enable"));
        assertEquals(null, dto.getUpdatedBy());
    }

    private static void assertRejectsRange(Consumer<ModerationConfidenceFallbackConfigDTO> mutator, String message) {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));
        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        mutator.accept(payload);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsert(payload, 1L, "u"));
        assertEquals(message, ex.getMessage());
    }

    private static ModerationConfidenceFallbackConfigEntity baseEntity() {
        ModerationConfidenceFallbackConfigEntity e = new ModerationConfidenceFallbackConfigEntity();
e.setLlmEnabled(Boolean.TRUE);
        e.setLlmRejectThreshold(0.75);
        e.setLlmHumanThreshold(0.5);
        e.setChunkLlmRejectThreshold(0.75);
        e.setChunkLlmHumanThreshold(0.5);
        e.setLlmTextRiskThreshold(0.80);
        e.setLlmImageRiskThreshold(0.30);
        e.setLlmStrongRejectThreshold(0.95);
        e.setLlmStrongPassThreshold(0.10);
        e.setLlmCrossModalThreshold(0.75);
        e.setReportHumanThreshold(5);
        e.setChunkThresholdChars(20000);
        e.setVersion(0);
        return e;
    }
}
