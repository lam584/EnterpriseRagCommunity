package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationConfidenceFallbackConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminModerationFallbackServiceBranchCoverageTest {

    @Test
    void upsertRejectsNullPayload() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(null, 1L, "u"));
    }

    @Test
    void upsertRejectsVecThresholdOutOfRangeOnBothSides() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO low = new ModerationConfidenceFallbackConfigDTO();
        low.setVecThreshold(-0.01);
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(low, 1L, "u"));

        ModerationConfidenceFallbackConfigDTO high = new ModerationConfidenceFallbackConfigDTO();
        high.setVecThreshold(2.01);
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(high, 1L, "u"));
    }

    @Test
    void upsertRejectsLlmThresholdOrderViolations() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO llmOrder = new ModerationConfidenceFallbackConfigDTO();
        llmOrder.setLlmRejectThreshold(0.3);
        llmOrder.setLlmHumanThreshold(0.4);
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(llmOrder, 1L, "u"));

        ModerationConfidenceFallbackConfigDTO strongOrder = new ModerationConfidenceFallbackConfigDTO();
        strongOrder.setLlmStrongRejectThreshold(0.4);
        strongOrder.setLlmStrongPassThreshold(0.5);
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(strongOrder, 1L, "u"));
    }

    @Test
    void upsertRejectsReportHumanThresholdOutOfRange() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO tooLow = new ModerationConfidenceFallbackConfigDTO();
        tooLow.setReportHumanThreshold(0);
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(tooLow, 1L, "u"));

        ModerationConfidenceFallbackConfigDTO tooHigh = new ModerationConfidenceFallbackConfigDTO();
        tooHigh.setReportHumanThreshold(1_000_001);
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(tooHigh, 1L, "u"));
    }

    @Test
    void upsertThrowsWhenSecondConfigLookupMissing() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()), Optional.empty());

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        assertThrows(IllegalStateException.class, () -> svc.upsert(payload, 1L, "u"));
    }

    @Test
    void upsertNormalizesAllThresholdBranchesAndStructuredValues() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));

        ModerationConfidenceFallbackConfigEntity existing = baseEntity();
        existing.setId(1L);
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> innerMap = new AbstractMap<>() {
            @Override
            public Set<Entry<String, Object>> entrySet() {
                LinkedHashSet<Entry<String, Object>> set = new LinkedHashSet<>();
                set.add(null);
                set.add(new SimpleEntry<>(null, 1));
                set.add(new SimpleEntry<>("inner", 2));
                return set;
            }
        };

        Map<String, Object> th = new LinkedHashMap<>();
        th.put("chunk.memory.maxChars", "400000");
        th.put("chunk.memory.maxEvidenceItems", -1);
        th.put("chunk.memory.maxEntities", 2000);
        th.put("chunk.prevSummary.maxChars", 5000);
        th.put("chunk.finalReview.enable", "yes");
        th.put("chunk.finalReview.triggerOpenQuestions", "n");
        th.put("chunk.imageStage.enable", 1);
        th.put("chunk.global.enable", "0");
        th.put("chunk.conflict.forceHuman", "true");
        th.put("chunk.finalReview.triggerScoreMin", -2.0);
        th.put("chunk.withImages.imageStrongRejectThreshold", "2.5");
        th.put("chunk.withImages.crossModalThreshold", "0.25");
        th.put("chunk.finalReview.triggerRiskTagCount", 5000L);
        th.put("llm.text.upgrade.enable", "y");
        th.put("llm.text.upgrade.scoreMin", "0.2");
        th.put("llm.text.upgrade.scoreMax", "1.3");
        th.put("llm.text.upgrade.uncertaintyMin", "-1");
        th.put("llm.cross.upgrade.enable", "false");
        th.put("llm.cross.upgrade.onConflict", "no");
        th.put("llm.cross.upgrade.onUncertainty", "1");
        th.put("llm.cross.upgrade.onGray", 0);
        th.put("llm.cross.upgrade.uncertaintyMin", "0.7");
        th.put("llm.cross.upgrade.scoreGrayMargin", "2");
        th.put("custom.string", "ok");
        th.put("custom.number", 42);
        th.put("custom.bool", Boolean.TRUE);
        th.put("custom.map", innerMap);
        th.put("custom.list", new ArrayList<>(java.util.List.of("a", "b")));
        th.put(null, "ignore");
        th.put(" ", "ignore");
        th.put("nullable", null);

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setThresholds(th);
        ModerationConfidenceFallbackConfigDTO out = svc.upsert(payload, 10L, "alice");

        assertEquals(200_000L, ((Number) out.getThresholds().get("chunk.memory.maxChars")).longValue());
        assertEquals(0L, ((Number) out.getThresholds().get("chunk.memory.maxEvidenceItems")).longValue());
        assertEquals(1_000L, ((Number) out.getThresholds().get("chunk.memory.maxEntities")).longValue());
        assertEquals(2_000L, ((Number) out.getThresholds().get("chunk.prevSummary.maxChars")).longValue());
        assertEquals(true, out.getThresholds().get("chunk.finalReview.enable"));
        assertEquals(false, out.getThresholds().get("chunk.finalReview.triggerOpenQuestions"));
        assertEquals(true, out.getThresholds().get("chunk.imageStage.enable"));
        assertEquals(false, out.getThresholds().get("chunk.global.enable"));
        assertEquals(true, out.getThresholds().get("chunk.conflict.forceHuman"));
        assertEquals(0.0, ((Number) out.getThresholds().get("chunk.finalReview.triggerScoreMin")).doubleValue());
        assertEquals(1.0, ((Number) out.getThresholds().get("chunk.withImages.imageStrongRejectThreshold")).doubleValue());
        assertEquals(0.25, ((Number) out.getThresholds().get("chunk.withImages.crossModalThreshold")).doubleValue());
        assertEquals(1_000L, ((Number) out.getThresholds().get("chunk.finalReview.triggerRiskTagCount")).longValue());
        assertEquals(true, out.getThresholds().get("llm.text.upgrade.enable"));
        assertEquals(0.2, ((Number) out.getThresholds().get("llm.text.upgrade.scoreMin")).doubleValue());
        assertEquals(1.0, ((Number) out.getThresholds().get("llm.text.upgrade.scoreMax")).doubleValue());
        assertEquals(0.0, ((Number) out.getThresholds().get("llm.text.upgrade.uncertaintyMin")).doubleValue());
        assertEquals(false, out.getThresholds().get("llm.cross.upgrade.enable"));
        assertEquals(false, out.getThresholds().get("llm.cross.upgrade.onConflict"));
        assertEquals(true, out.getThresholds().get("llm.cross.upgrade.onUncertainty"));
        assertEquals(false, out.getThresholds().get("llm.cross.upgrade.onGray"));
        assertEquals(0.7, ((Number) out.getThresholds().get("llm.cross.upgrade.uncertaintyMin")).doubleValue());
        assertEquals(1.0, ((Number) out.getThresholds().get("llm.cross.upgrade.scoreGrayMargin")).doubleValue());
        assertEquals("ok", out.getThresholds().get("custom.string"));
        assertEquals(42, out.getThresholds().get("custom.number"));
        assertEquals(Boolean.TRUE, out.getThresholds().get("custom.bool"));
        assertEquals(2, ((Map<?, ?>) out.getThresholds().get("custom.map")).get("inner"));
        assertEquals(java.util.List.of("a", "b"), out.getThresholds().get("custom.list"));
        assertEquals(null, out.getThresholds().get("nullable"));
    }

    @Test
    void upsertRejectsInvalidBooleanStringForThreshold() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setThresholds(Map.of("llm.text.upgrade.enable", "not-bool"));
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsInvalidDoubleStringForThreshold() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setThresholds(Map.of("llm.text.upgrade.scoreMin", "bad-number"));
        assertThrows(IllegalArgumentException.class, () -> svc.upsert(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsUnsupportedThresholdValueType() {
        ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
        AdminModerationFallbackService svc = new AdminModerationFallbackService(repo, mock(AuditLogWriter.class), mock(AuditDiffBuilder.class));
        when(repo.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseEntity()));

        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("custom.unsupported", new StringBuilder("x"));
        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        payload.setThresholds(thresholds);

        assertThrows(IllegalArgumentException.class, () -> svc.upsert(payload, 1L, "u"));
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
