package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationConfidenceFallbackConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminModerationFallbackServiceChunkThresholdTest {

    @Test
    void upsertRejectsChunkThresholdTooSmall() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setChunkThresholdChars(999);

        assertThrows(IllegalArgumentException.class, () -> svc.upsert(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsChunkThresholdTooLarge() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setChunkThresholdChars(5_000_001);

        assertThrows(IllegalArgumentException.class, () -> svc.upsert(payload, 1L, "u"));
    }

    @Test
    void upsertPersistsChunkThreshold() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        ModerationConfidenceFallbackConfigEntity existing = baseEntity();
        existing.setId(1L);

        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setChunkThresholdChars(30_000);

        ModerationConfidenceFallbackConfigDTO out = svc.upsert(payload, 10L, "alice");
        assertEquals(30_000, out.getChunkThresholdChars());
    }

    @Test
    void upsertRejectsChunkLlmThresholdInvalidOrder() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setChunkLlmRejectThreshold(0.3);
        payload.setChunkLlmHumanThreshold(0.4);

        assertThrows(IllegalArgumentException.class, () -> svc.upsert(payload, 1L, "u"));
    }

    @Test
    void upsertPersistsChunkLlmThresholds() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        ModerationConfidenceFallbackConfigEntity existing = baseEntity();
        existing.setId(1L);

        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setChunkLlmRejectThreshold(0.9);
        payload.setChunkLlmHumanThreshold(0.2);

        ModerationConfidenceFallbackConfigDTO out = svc.upsert(payload, 10L, "alice");
        assertEquals(0.9, out.getChunkLlmRejectThreshold());
        assertEquals(0.2, out.getChunkLlmHumanThreshold());
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
