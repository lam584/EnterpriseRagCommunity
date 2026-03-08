package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModerationChunkReviewConfigService {
    public static final String KEY_CONFIG_JSON = "moderation.chunk_review.config.json";
    private static final long CACHE_TTL_MS = 5_000L;

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    private volatile ModerationChunkReviewConfigDTO cached;
    private volatile long cachedAtMs;

    @Transactional
    public ModerationChunkReviewConfigDTO getConfig() {
        long now = System.currentTimeMillis();
        ModerationChunkReviewConfigDTO c = cached;
        long age = now - cachedAtMs;
        if (c != null && age >= 0 && age < CACHE_TTL_MS) return c;

        String json = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        ModerationChunkReviewConfigDTO out;
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("missing config: " + KEY_CONFIG_JSON);
        }
        try {
            ModerationChunkReviewConfigDTO cfg = objectMapper.readValue(json, ModerationChunkReviewConfigDTO.class);
            out = normalize(cfg);
        } catch (Exception e) {
            throw new IllegalStateException("invalid config: " + KEY_CONFIG_JSON + ", err=" + e.getMessage(), e);
        }
        cached = out;
        cachedAtMs = now;
        return out;
    }

    @Transactional
    public ModerationChunkReviewConfigDTO updateConfig(ModerationChunkReviewConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        ModerationChunkReviewConfigDTO before = getConfig();
        ModerationChunkReviewConfigDTO merged = merge(before, payload);
        ModerationChunkReviewConfigDTO cfg = normalize(merged);
        try {
            appSettingsService.upsertString(KEY_CONFIG_JSON, objectMapper.writeValueAsString(cfg));
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
        cached = cfg;
        cachedAtMs = System.currentTimeMillis();
        return cfg;
    }

    private ModerationChunkReviewConfigDTO normalize(ModerationChunkReviewConfigDTO in) {
        if (in == null) throw new IllegalStateException("invalid config: " + KEY_CONFIG_JSON + " is null");
        ModerationChunkReviewConfigDTO dto = in;

        dto.setEnabled(requireBool("enabled", dto.getEnabled()));
        dto.setEnableTempIndexHints(requireBool("enableTempIndexHints", dto.getEnableTempIndexHints()));
        dto.setEnableContextCompress(requireBool("enableContextCompress", dto.getEnableContextCompress()));
        dto.setEnableGlobalMemory(requireBool("enableGlobalMemory", dto.getEnableGlobalMemory()));
        dto.setSendImagesOnlyWhenInEvidence(dto.getSendImagesOnlyWhenInEvidence() == null ? Boolean.TRUE : dto.getSendImagesOnlyWhenInEvidence());
        dto.setIncludeImagesBlockOnlyForEvidenceMatches(
            dto.getIncludeImagesBlockOnlyForEvidenceMatches() == null ? Boolean.TRUE : dto.getIncludeImagesBlockOnlyForEvidenceMatches()
        );
        dto.setQueueAutoRefreshEnabled(requireBool("queueAutoRefreshEnabled", dto.getQueueAutoRefreshEnabled()));

        dto.setChunkThresholdChars(clampIntRequired("chunkThresholdChars", dto.getChunkThresholdChars(), 1000, 5_000_000));
        dto.setChunkSizeChars(clampIntRequired("chunkSizeChars", dto.getChunkSizeChars(), 500, 10_000));
        dto.setOverlapChars(clampIntRequired("overlapChars", dto.getOverlapChars(), 0, 2_000));
        dto.setMaxChunksTotal(clampIntRequired("maxChunksTotal", dto.getMaxChunksTotal(), 1, 2000));
        dto.setChunksPerRun(clampIntRequired("chunksPerRun", dto.getChunksPerRun(), 1, 50));
        dto.setMaxConcurrentWorkers(clampIntRequired("maxConcurrentWorkers", dto.getMaxConcurrentWorkers(), 1, 64));
        dto.setMaxAttempts(clampIntRequired("maxAttempts", dto.getMaxAttempts(), 1, 20));
        dto.setQueuePollIntervalMs(clampIntRequired("queuePollIntervalMs", dto.getQueuePollIntervalMs(), 1000, 60_000));

        if (dto.getOverlapChars() >= dto.getChunkSizeChars()) {
            dto.setOverlapChars(Math.max(0, dto.getChunkSizeChars() / 10));
        }
        return dto;
    }

    private static Integer clampIntRequired(String field, Integer v, int min, int max) {
        if (v == null) throw new IllegalStateException("invalid config: " + KEY_CONFIG_JSON + ", missing field: " + field);
        int x = v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static Boolean requireBool(String field, Boolean v) {
        if (v == null) throw new IllegalStateException("invalid config: " + KEY_CONFIG_JSON + ", missing field: " + field);
        return v;
    }

    private static ModerationChunkReviewConfigDTO merge(ModerationChunkReviewConfigDTO base, ModerationChunkReviewConfigDTO payload) {
        ModerationChunkReviewConfigDTO out = new ModerationChunkReviewConfigDTO();
        if (base == null) throw new IllegalStateException("missing config: " + KEY_CONFIG_JSON);
        ModerationChunkReviewConfigDTO b = base;

        out.setEnabled(b.getEnabled());
        out.setChunkThresholdChars(b.getChunkThresholdChars());
        out.setChunkSizeChars(b.getChunkSizeChars());
        out.setOverlapChars(b.getOverlapChars());
        out.setMaxChunksTotal(b.getMaxChunksTotal());
        out.setChunksPerRun(b.getChunksPerRun());
        out.setMaxConcurrentWorkers(b.getMaxConcurrentWorkers());
        out.setMaxAttempts(b.getMaxAttempts());
        out.setEnableTempIndexHints(b.getEnableTempIndexHints());
        out.setEnableContextCompress(b.getEnableContextCompress());
        out.setEnableGlobalMemory(b.getEnableGlobalMemory());
        out.setSendImagesOnlyWhenInEvidence(b.getSendImagesOnlyWhenInEvidence());
        out.setIncludeImagesBlockOnlyForEvidenceMatches(b.getIncludeImagesBlockOnlyForEvidenceMatches());
        out.setQueueAutoRefreshEnabled(b.getQueueAutoRefreshEnabled());
        out.setQueuePollIntervalMs(b.getQueuePollIntervalMs());

        if (payload.getEnabled() != null) out.setEnabled(payload.getEnabled());
        if (payload.getChunkThresholdChars() != null) out.setChunkThresholdChars(payload.getChunkThresholdChars());
        if (payload.getChunkSizeChars() != null) out.setChunkSizeChars(payload.getChunkSizeChars());
        if (payload.getOverlapChars() != null) out.setOverlapChars(payload.getOverlapChars());
        if (payload.getMaxChunksTotal() != null) out.setMaxChunksTotal(payload.getMaxChunksTotal());
        if (payload.getChunksPerRun() != null) out.setChunksPerRun(payload.getChunksPerRun());
        if (payload.getMaxConcurrentWorkers() != null) out.setMaxConcurrentWorkers(payload.getMaxConcurrentWorkers());
        if (payload.getMaxAttempts() != null) out.setMaxAttempts(payload.getMaxAttempts());
        if (payload.getEnableTempIndexHints() != null) out.setEnableTempIndexHints(payload.getEnableTempIndexHints());
        if (payload.getEnableContextCompress() != null) out.setEnableContextCompress(payload.getEnableContextCompress());
        if (payload.getEnableGlobalMemory() != null) out.setEnableGlobalMemory(payload.getEnableGlobalMemory());
        if (payload.getSendImagesOnlyWhenInEvidence() != null) out.setSendImagesOnlyWhenInEvidence(payload.getSendImagesOnlyWhenInEvidence());
        if (payload.getIncludeImagesBlockOnlyForEvidenceMatches() != null) {
            out.setIncludeImagesBlockOnlyForEvidenceMatches(payload.getIncludeImagesBlockOnlyForEvidenceMatches());
        }
        if (payload.getQueueAutoRefreshEnabled() != null) out.setQueueAutoRefreshEnabled(payload.getQueueAutoRefreshEnabled());
        if (payload.getQueuePollIntervalMs() != null) out.setQueuePollIntervalMs(payload.getQueuePollIntervalMs());
        return out;
    }
}
