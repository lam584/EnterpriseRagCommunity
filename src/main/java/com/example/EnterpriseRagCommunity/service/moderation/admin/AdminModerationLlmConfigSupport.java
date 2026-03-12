package com.example.EnterpriseRagCommunity.service.moderation.admin;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;

import lombok.RequiredArgsConstructor;

record ConfigUpsertResult(
        ModerationLlmConfigEntity saved,
        Map<String, Object> beforeSummary,
        Map<String, Object> afterSummary
) {}

@Component
@RequiredArgsConstructor
class AdminModerationLlmConfigSupport {

    private static final long BASE_CONFIG_CACHE_TTL_MS = 5_000L;

    private final ModerationLlmConfigRepository configRepository;

    private volatile ModerationLlmConfigEntity cachedBaseConfig;
    private volatile long cachedBaseConfigAtMs;

    ModerationLlmConfigEntity loadBaseConfigCached() {
        long now = System.currentTimeMillis();
        ModerationLlmConfigEntity cached = this.cachedBaseConfig;
        long age = now - this.cachedBaseConfigAtMs;
        if (cached != null && age >= 0 && age < BASE_CONFIG_CACHE_TTL_MS) return cached;

        ModerationLlmConfigEntity cfg = configRepository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseThrow(() -> new IllegalStateException("moderation_llm_config not initialized"));
        cfg = normalizeBaseConfig(cfg);
        this.cachedBaseConfig = cfg;
        this.cachedBaseConfigAtMs = now;
        return cfg;
    }

    void invalidateBaseConfigCache() {
        this.cachedBaseConfig = null;
        this.cachedBaseConfigAtMs = 0L;
    }

    ConfigUpsertResult upsertConfigEntity(LlmModerationConfigDTO payload, Long actorUserId) {
        if (payload == null) throw new IllegalArgumentException("payload cannot be null");

        if (payload.getMultimodalPromptCode() == null || payload.getMultimodalPromptCode().isBlank()) {
            throw new IllegalArgumentException("multimodalPromptCode 娑撳秷鍏樻稉铏光敄");
        }
        if (payload.getJudgePromptCode() == null || payload.getJudgePromptCode().isBlank()) {
            throw new IllegalArgumentException("judgePromptCode 娑撳秷鍏樻稉铏光敄");
        }

        ModerationLlmConfigEntity cfg = configRepository.findTopByOrderByUpdatedAtDescIdDesc().orElse(null);
        if (cfg == null) cfg = new ModerationLlmConfigEntity();
        Map<String, Object> before = summarizeConfig(cfg);

        cfg.setMultimodalPromptCode(payload.getMultimodalPromptCode());
        cfg.setJudgePromptCode(payload.getJudgePromptCode());
        cfg.setAutoRun(payload.getAutoRun() != null ? payload.getAutoRun() : Boolean.TRUE);
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(actorUserId);

        cfg = configRepository.save(cfg);
        invalidateBaseConfigCache();

        Map<String, Object> after = summarizeConfig(cfg);
        return new ConfigUpsertResult(cfg, before, after);
    }

    ModerationLlmConfigEntity merge(ModerationLlmConfigEntity base, LlmModerationTestRequest.LlmModerationConfigOverrideDTO o) {
        ModerationLlmConfigEntity m = new ModerationLlmConfigEntity();
        m.setId(base.getId());
        m.setMultimodalPromptCode(base.getMultimodalPromptCode());
        m.setJudgePromptCode(base.getJudgePromptCode());
        m.setAutoRun(base.getAutoRun());
        m.setVersion(base.getVersion());
        m.setUpdatedAt(base.getUpdatedAt());
        m.setUpdatedBy(base.getUpdatedBy());

        if (o == null) return m;
        if (o.getAutoRun() != null) m.setAutoRun(o.getAutoRun());
        return m;
    }

    ModerationLlmConfigEntity normalizeBaseConfig(ModerationLlmConfigEntity base) {
        if (base == null) throw new IllegalStateException("moderation_llm_config not initialized");

        if (base.getMultimodalPromptCode() == null || base.getMultimodalPromptCode().isBlank()) {
            throw new IllegalStateException("moderation_llm_config.multimodal_prompt_code is required");
        }
        if (base.getJudgePromptCode() == null || base.getJudgePromptCode().isBlank()) {
            throw new IllegalStateException("moderation_llm_config.judge_prompt_code is required");
        }
        return base;
    }

    LlmModerationConfigDTO toDto(ModerationLlmConfigEntity e, String updatedByName) {
        LlmModerationConfigDTO dto = new LlmModerationConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setMultimodalPromptCode(e.getMultimodalPromptCode());
        dto.setJudgePromptCode(e.getJudgePromptCode());
        dto.setAutoRun(e.getAutoRun());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }

    static Map<String, Object> summarizeConfig(ModerationLlmConfigEntity cfg) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (cfg == null) return out;
        out.put("multimodalPromptCode", cfg.getMultimodalPromptCode());
        out.put("judgePromptCode", cfg.getJudgePromptCode());
        out.put("autoRun", cfg.getAutoRun());
        return out;
    }

    static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}


