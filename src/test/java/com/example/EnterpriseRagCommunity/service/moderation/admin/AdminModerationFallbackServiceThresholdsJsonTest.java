package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationConfidenceFallbackConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminModerationFallbackServiceThresholdsJsonTest {

    @Test
    void getConfigThrowsWhenEntityNull() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        assertThrows(IllegalStateException.class, svc::getConfig);
    }

    @Test
    void getConfigReturnsEmptyThresholdsWhenEntityHasNullThresholds() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        ModerationConfidenceFallbackConfigEntity e = baseEntity();
        e.setId(1L);
        e.setThresholds(null);
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(e));

        ModerationConfidenceFallbackConfigDTO dto = svc.getConfig();
        assertNotNull(dto.getThresholds());
        assertEquals(0, dto.getThresholds().size());
    }

    @Test
    void upsertRejectsInvalidThresholdValueType() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setThresholds(Map.of("chunk.memory.maxChars", "abc"));

        assertThrows(IllegalArgumentException.class, () -> svc.upsert(payload, 1L, "u"));
    }

    @Test
    void upsertClampsKnownThresholds() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        ModerationConfidenceFallbackConfigEntity existing = baseEntity();
        existing.setId(1L);
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> th = new LinkedHashMap<>();
        th.put("chunk.prevSummary.maxChars", 5000);
        th.put("chunk.memory.maxEvidenceItems", -1);
        th.put("chunk.finalReview.triggerScoreMin", 2.0);
        th.put("chunk.finalReview.enable", 1);

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setThresholds(th);

        ModerationConfidenceFallbackConfigDTO out = svc.upsert(payload, 10L, "alice");
        assertEquals(2000L, ((Number) out.getThresholds().get("chunk.prevSummary.maxChars")).longValue());
        assertEquals(0L, ((Number) out.getThresholds().get("chunk.memory.maxEvidenceItems")).longValue());
        assertEquals(1.0, ((Number) out.getThresholds().get("chunk.finalReview.triggerScoreMin")).doubleValue());
        assertEquals(true, out.getThresholds().get("chunk.finalReview.enable"));
    }

    private static ModerationConfidenceFallbackConfigEntity baseEntity() {
        ModerationConfidenceFallbackConfigEntity e = new ModerationConfidenceFallbackConfigEntity();
        e.setRuleEnabled(Boolean.TRUE);
        e.setRuleHighAction(ModerationConfidenceFallbackConfigEntity.Action.HUMAN);
        e.setRuleMediumAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        e.setRuleLowAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        e.setVecEnabled(Boolean.TRUE);
        e.setVecThreshold(0.2);
        e.setVecHitAction(ModerationConfidenceFallbackConfigEntity.Action.HUMAN);
        e.setVecMissAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
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
