package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationConfidenceFallbackConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class AdminModerationFallbackService {

    private final ModerationConfidenceFallbackConfigRepository repository;

    @Transactional(readOnly = true)
    public ModerationConfidenceFallbackConfigDTO getConfig() {
        ModerationConfidenceFallbackConfigEntity cfg = repository.findAll().stream()
                .max(Comparator.comparing(ModerationConfidenceFallbackConfigEntity::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        if (cfg == null) cfg = defaultEntity();
        return toDto(cfg, null);
    }

    @Transactional
    public ModerationConfidenceFallbackConfigDTO upsert(ModerationConfidenceFallbackConfigDTO payload, Long actorUserId, String actorUsername) {
        if (payload == null) throw new IllegalArgumentException("body is required");

        // basic validation
        if (payload.getVecThreshold() != null) {
            double t = payload.getVecThreshold();
            if (t < 0 || t > 2) throw new IllegalArgumentException("vecThreshold out of range");
        }
        if (payload.getLlmRejectThreshold() != null && (payload.getLlmRejectThreshold() < 0 || payload.getLlmRejectThreshold() > 1)) {
            throw new IllegalArgumentException("llmRejectThreshold must be within [0,1]");
        }
        if (payload.getLlmHumanThreshold() != null && (payload.getLlmHumanThreshold() < 0 || payload.getLlmHumanThreshold() > 1)) {
            throw new IllegalArgumentException("llmHumanThreshold must be within [0,1]");
        }
        if (payload.getLlmRejectThreshold() != null && payload.getLlmHumanThreshold() != null
                && payload.getLlmHumanThreshold() > payload.getLlmRejectThreshold()) {
            throw new IllegalArgumentException("llmHumanThreshold cannot be greater than llmRejectThreshold");
        }
        if (payload.getLlmTextRiskThreshold() != null && (payload.getLlmTextRiskThreshold() < 0 || payload.getLlmTextRiskThreshold() > 1)) {
            throw new IllegalArgumentException("llmTextRiskThreshold must be within [0,1]");
        }
        if (payload.getLlmImageRiskThreshold() != null && (payload.getLlmImageRiskThreshold() < 0 || payload.getLlmImageRiskThreshold() > 1)) {
            throw new IllegalArgumentException("llmImageRiskThreshold must be within [0,1]");
        }
        if (payload.getLlmStrongRejectThreshold() != null && (payload.getLlmStrongRejectThreshold() < 0 || payload.getLlmStrongRejectThreshold() > 1)) {
            throw new IllegalArgumentException("llmStrongRejectThreshold must be within [0,1]");
        }
        if (payload.getLlmStrongPassThreshold() != null && (payload.getLlmStrongPassThreshold() < 0 || payload.getLlmStrongPassThreshold() > 1)) {
            throw new IllegalArgumentException("llmStrongPassThreshold must be within [0,1]");
        }
        if (payload.getLlmCrossModalThreshold() != null && (payload.getLlmCrossModalThreshold() < 0 || payload.getLlmCrossModalThreshold() > 1)) {
            throw new IllegalArgumentException("llmCrossModalThreshold must be within [0,1]");
        }
        if (payload.getLlmStrongRejectThreshold() != null && payload.getLlmStrongPassThreshold() != null
                && payload.getLlmStrongPassThreshold() > payload.getLlmStrongRejectThreshold()) {
            throw new IllegalArgumentException("llmStrongPassThreshold cannot be greater than llmStrongRejectThreshold");
        }
        if (payload.getReportHumanThreshold() != null) {
            int t = payload.getReportHumanThreshold();
            if (t < 1 || t > 1000000) throw new IllegalArgumentException("reportHumanThreshold out of range");
        }

        ModerationConfidenceFallbackConfigEntity cfg = repository.findAll().stream().findFirst().orElseGet(this::defaultEntity);

        cfg.setRuleEnabled(payload.getRuleEnabled() != null ? payload.getRuleEnabled() : cfg.getRuleEnabled());
        if (payload.getRuleHighAction() != null) cfg.setRuleHighAction(payload.getRuleHighAction());
        if (payload.getRuleMediumAction() != null) cfg.setRuleMediumAction(payload.getRuleMediumAction());
        if (payload.getRuleLowAction() != null) cfg.setRuleLowAction(payload.getRuleLowAction());

        cfg.setVecEnabled(payload.getVecEnabled() != null ? payload.getVecEnabled() : cfg.getVecEnabled());
        if (payload.getVecThreshold() != null) cfg.setVecThreshold(payload.getVecThreshold());
        if (payload.getVecHitAction() != null) cfg.setVecHitAction(payload.getVecHitAction());
        if (payload.getVecMissAction() != null) cfg.setVecMissAction(payload.getVecMissAction());

        cfg.setLlmEnabled(payload.getLlmEnabled() != null ? payload.getLlmEnabled() : cfg.getLlmEnabled());
        if (payload.getLlmRejectThreshold() != null) cfg.setLlmRejectThreshold(payload.getLlmRejectThreshold());
        if (payload.getLlmHumanThreshold() != null) cfg.setLlmHumanThreshold(payload.getLlmHumanThreshold());
        if (payload.getLlmTextRiskThreshold() != null) cfg.setLlmTextRiskThreshold(payload.getLlmTextRiskThreshold());
        if (payload.getLlmImageRiskThreshold() != null) cfg.setLlmImageRiskThreshold(payload.getLlmImageRiskThreshold());
        if (payload.getLlmStrongRejectThreshold() != null) cfg.setLlmStrongRejectThreshold(payload.getLlmStrongRejectThreshold());
        if (payload.getLlmStrongPassThreshold() != null) cfg.setLlmStrongPassThreshold(payload.getLlmStrongPassThreshold());
        if (payload.getLlmCrossModalThreshold() != null) cfg.setLlmCrossModalThreshold(payload.getLlmCrossModalThreshold());

        if (payload.getReportHumanThreshold() != null) cfg.setReportHumanThreshold(payload.getReportHumanThreshold());

        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(actorUserId);

        cfg = repository.save(cfg);
        return toDto(cfg, actorUsername);
    }

    public ModerationConfidenceFallbackConfigEntity defaultEntity() {
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
        e.setLlmTextRiskThreshold(0.80);
        e.setLlmImageRiskThreshold(0.30);
        e.setLlmStrongRejectThreshold(0.95);
        e.setLlmStrongPassThreshold(0.10);
        e.setLlmCrossModalThreshold(0.75);

        e.setReportHumanThreshold(5);

        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private static ModerationConfidenceFallbackConfigDTO toDto(ModerationConfidenceFallbackConfigEntity e, String updatedByName) {
        ModerationConfidenceFallbackConfigDTO dto = new ModerationConfidenceFallbackConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());

        dto.setRuleEnabled(e.getRuleEnabled());
        dto.setRuleHighAction(e.getRuleHighAction());
        dto.setRuleMediumAction(e.getRuleMediumAction());
        dto.setRuleLowAction(e.getRuleLowAction());

        dto.setVecEnabled(e.getVecEnabled());
        dto.setVecThreshold(e.getVecThreshold());
        dto.setVecHitAction(e.getVecHitAction());
        dto.setVecMissAction(e.getVecMissAction());

        dto.setLlmEnabled(e.getLlmEnabled());
        dto.setLlmRejectThreshold(e.getLlmRejectThreshold());
        dto.setLlmHumanThreshold(e.getLlmHumanThreshold());
        dto.setLlmTextRiskThreshold(e.getLlmTextRiskThreshold());
        dto.setLlmImageRiskThreshold(e.getLlmImageRiskThreshold());
        dto.setLlmStrongRejectThreshold(e.getLlmStrongRejectThreshold());
        dto.setLlmStrongPassThreshold(e.getLlmStrongPassThreshold());
        dto.setLlmCrossModalThreshold(e.getLlmCrossModalThreshold());

        dto.setReportHumanThreshold(e.getReportHumanThreshold());

        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }
}
