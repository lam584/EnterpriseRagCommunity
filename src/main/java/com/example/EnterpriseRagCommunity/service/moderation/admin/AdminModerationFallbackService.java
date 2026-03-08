package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationConfidenceFallbackConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminModerationFallbackService {

    private final ModerationConfidenceFallbackConfigRepository repository;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @Transactional(readOnly = true)
    public ModerationConfidenceFallbackConfigDTO getConfig() {
        ModerationConfidenceFallbackConfigEntity cfg = repository.findFirstByOrderByUpdatedAtDescIdDesc().orElse(null);

        if (cfg == null) throw new IllegalStateException("moderation_confidence_fallback_config not initialized");
        return toDto(cfg, null);
    }

    @Transactional
    public ModerationConfidenceFallbackConfigDTO upsert(ModerationConfidenceFallbackConfigDTO payload, Long actorUserId, String actorUsername) {
        if (payload == null) throw new IllegalArgumentException("body is required");

        ModerationConfidenceFallbackConfigDTO beforeDto = getConfig();

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
        if (payload.getChunkLlmRejectThreshold() != null && (payload.getChunkLlmRejectThreshold() < 0 || payload.getChunkLlmRejectThreshold() > 1)) {
            throw new IllegalArgumentException("chunkLlmRejectThreshold must be within [0,1]");
        }
        if (payload.getChunkLlmHumanThreshold() != null && (payload.getChunkLlmHumanThreshold() < 0 || payload.getChunkLlmHumanThreshold() > 1)) {
            throw new IllegalArgumentException("chunkLlmHumanThreshold must be within [0,1]");
        }
        if (payload.getChunkLlmRejectThreshold() != null && payload.getChunkLlmHumanThreshold() != null
                && payload.getChunkLlmHumanThreshold() > payload.getChunkLlmRejectThreshold()) {
            throw new IllegalArgumentException("chunkLlmHumanThreshold cannot be greater than chunkLlmRejectThreshold");
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
        if (payload.getChunkThresholdChars() != null) {
            int t = payload.getChunkThresholdChars();
            if (t < 1000 || t > 5_000_000) throw new IllegalArgumentException("chunkThresholdChars out of range");
        }
        Map<String, Object> normalizedThresholds = payload.getThresholds() == null ? null : normalizeThresholds(payload.getThresholds());

        ModerationConfidenceFallbackConfigEntity cfg = repository.findFirstByOrderByUpdatedAtDescIdDesc()
                .orElseThrow(() -> new IllegalStateException("moderation_confidence_fallback_config not initialized"));

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
        if (payload.getChunkLlmRejectThreshold() != null) cfg.setChunkLlmRejectThreshold(payload.getChunkLlmRejectThreshold());
        if (payload.getChunkLlmHumanThreshold() != null) cfg.setChunkLlmHumanThreshold(payload.getChunkLlmHumanThreshold());
        if (payload.getLlmTextRiskThreshold() != null) cfg.setLlmTextRiskThreshold(payload.getLlmTextRiskThreshold());
        if (payload.getLlmImageRiskThreshold() != null) cfg.setLlmImageRiskThreshold(payload.getLlmImageRiskThreshold());
        if (payload.getLlmStrongRejectThreshold() != null) cfg.setLlmStrongRejectThreshold(payload.getLlmStrongRejectThreshold());
        if (payload.getLlmStrongPassThreshold() != null) cfg.setLlmStrongPassThreshold(payload.getLlmStrongPassThreshold());
        if (payload.getLlmCrossModalThreshold() != null) cfg.setLlmCrossModalThreshold(payload.getLlmCrossModalThreshold());

        if (payload.getReportHumanThreshold() != null) cfg.setReportHumanThreshold(payload.getReportHumanThreshold());
        if (payload.getChunkThresholdChars() != null) cfg.setChunkThresholdChars(payload.getChunkThresholdChars());
        if (normalizedThresholds != null) cfg.setThresholds(normalizedThresholds);

        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(actorUserId);

        cfg = repository.save(cfg);
        ModerationConfidenceFallbackConfigDTO afterDto = toDto(cfg, actorUsername);
        auditLogWriter.write(
                actorUserId,
                actorUsername,
                "CONFIG_CHANGE",
                "MODERATION_FALLBACK_CONFIG",
                cfg.getId(),
                AuditResult.SUCCESS,
                "更新置信回退机制配置",
                null,
                auditDiffBuilder.build(beforeDto, afterDto)
        );
        return afterDto;
    }

    private static Map<String, Object> normalizeThresholds(Map<String, Object> in) {
        if (in == null) return null;
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> en : in.entrySet()) {
            if (en == null) continue;
            String key = en.getKey() == null ? null : en.getKey().trim();
            if (key == null || key.isEmpty()) continue;
            Object v = en.getValue();
            if (v == null) {
                out.put(key, null);
                continue;
            }
            if (key.equals("chunk.memory.maxChars")) {
                out.put(key, clampLong(asLongStrict(key, v), 500L, 200_000L));
                continue;
            }
            if (key.equals("chunk.memory.maxEvidenceItems")) {
                out.put(key, clampLong(asLongStrict(key, v), 0L, 1_000L));
                continue;
            }
            if (key.equals("chunk.memory.maxEntities")) {
                out.put(key, clampLong(asLongStrict(key, v), 0L, 1_000L));
                continue;
            }
            if (key.equals("chunk.prevSummary.maxChars")) {
                out.put(key, clampLong(asLongStrict(key, v), 0L, 2_000L));
                continue;
            }
            if (key.equals("chunk.finalReview.enable")
                    || key.equals("chunk.finalReview.triggerOpenQuestions")
                    || key.equals("chunk.imageStage.enable")
                    || key.equals("chunk.global.enable")
                    || key.equals("chunk.conflict.forceHuman")) {
                out.put(key, asBooleanStrict(key, v));
                continue;
            }
            if (key.equals("chunk.finalReview.triggerScoreMin")
                    || key.equals("chunk.withImages.imageStrongRejectThreshold")
                    || key.equals("chunk.withImages.crossModalThreshold")) {
                out.put(key, clamp01(asDoubleStrict(key, v)));
                continue;
            }
            if (key.equals("chunk.finalReview.triggerRiskTagCount")) {
                out.put(key, clampLong(asLongStrict(key, v), 0L, 1_000L));
                continue;
            }
            if (key.equals("llm.text.upgrade.enable")) {
                out.put(key, asBooleanStrict(key, v));
                continue;
            }
            if (key.equals("llm.text.upgrade.scoreMin")
                    || key.equals("llm.text.upgrade.scoreMax")
                    || key.equals("llm.text.upgrade.uncertaintyMin")) {
                out.put(key, clamp01(asDoubleStrict(key, v)));
                continue;
            }
            if (key.equals("llm.cross.upgrade.enable")
                    || key.equals("llm.cross.upgrade.onConflict")
                    || key.equals("llm.cross.upgrade.onUncertainty")
                    || key.equals("llm.cross.upgrade.onGray")) {
                out.put(key, asBooleanStrict(key, v));
                continue;
            }
            if (key.equals("llm.cross.upgrade.uncertaintyMin")
                    || key.equals("llm.cross.upgrade.scoreGrayMargin")) {
                out.put(key, clamp01(asDoubleStrict(key, v)));
                continue;
            }
            if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                out.put(key, v);
                continue;
            }
            if (v instanceof Map<?, ?> m) {
                out.put(key, new LinkedHashMap<>(castMapStringObject(key, m)));
                continue;
            }
            if (v instanceof List<?> l) {
                out.put(key, new ArrayList<>(l));
                continue;
            }
            throw new IllegalArgumentException("thresholds contains unsupported value type for key=" + key);
        }
        return out;
    }

    private static Map<String, Object> castMapStringObject(String key, Map<?, ?> in) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> en : in.entrySet()) {
            if (en == null) continue;
            Object k0 = en.getKey();
            if (k0 == null) continue;
            String k = String.valueOf(k0);
            out.put(k, en.getValue());
        }
        return out;
    }

    private static long asLongStrict(String key, Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception e) {
            }
        }
        throw new IllegalArgumentException("thresholds key=" + key + " must be integer");
    }

    private static double asDoubleStrict(String key, Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception e) {
            }
        }
        throw new IllegalArgumentException("thresholds key=" + key + " must be number");
    }

    private static boolean asBooleanStrict(String key, Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v instanceof String s) {
            String t = s.trim().toLowerCase();
            if (t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("y")) return true;
            if (t.equals("false") || t.equals("0") || t.equals("no") || t.equals("n")) return false;
        }
        throw new IllegalArgumentException("thresholds key=" + key + " must be boolean");
    }

    private static long clampLong(long v, long min, long max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
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
        dto.setChunkLlmRejectThreshold(e.getChunkLlmRejectThreshold());
        dto.setChunkLlmHumanThreshold(e.getChunkLlmHumanThreshold());
        dto.setLlmTextRiskThreshold(e.getLlmTextRiskThreshold());
        dto.setLlmImageRiskThreshold(e.getLlmImageRiskThreshold());
        dto.setLlmStrongRejectThreshold(e.getLlmStrongRejectThreshold());
        dto.setLlmStrongPassThreshold(e.getLlmStrongPassThreshold());
        dto.setLlmCrossModalThreshold(e.getLlmCrossModalThreshold());

        dto.setReportHumanThreshold(e.getReportHumanThreshold());
        dto.setChunkThresholdChars(e.getChunkThresholdChars());
        dto.setThresholds(e.getThresholds() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(e.getThresholds()));

        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }
}
