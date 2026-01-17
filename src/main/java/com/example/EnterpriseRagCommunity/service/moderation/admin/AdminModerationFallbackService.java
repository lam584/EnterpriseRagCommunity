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

        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }
}
