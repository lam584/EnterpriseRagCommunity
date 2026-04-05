package com.example.EnterpriseRagCommunity.service.retrieval.jobs;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.service.retrieval.RagValueSupport;

final class RagAutoSyncSupport {

    private RagAutoSyncSupport() {
    }

    static boolean shouldRun(RagAutoSyncConfigDTO cfg, AtomicLong lastRunAtMs) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
            return false;
        }
        long intervalMs = (cfg.getIntervalSeconds() == null ? 30 : cfg.getIntervalSeconds()) * 1000L;
        intervalMs = Math.max(5000L, Math.min(3_600_000L, intervalMs));
        long now = System.currentTimeMillis();
        long previous = lastRunAtMs.get();
        if (now - previous < intervalMs) {
            return false;
        }
        return lastRunAtMs.compareAndSet(previous, now);
    }

    static String sourceType(Map<String, Object> metadata) {
        return RagValueSupport.sourceType(metadata);
    }

    static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isBlank()) {
                return null;
            }
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Long toLong(Object value) {
        return RagValueSupport.toLong(value);
    }

    static BuildChunkParams buildChunkParams(Map<String, Object> metadata) {
        return new BuildChunkParams(
                toInt(metadata == null ? null : metadata.get("lastBuildChunkMaxChars")),
                toInt(metadata == null ? null : metadata.get("lastBuildChunkOverlapChars")),
                toInt(metadata == null ? null : metadata.get("lastBuildEmbeddingDims"))
        );
    }

    record BuildChunkParams(
            Integer chunkMaxChars,
            Integer chunkOverlapChars,
            Integer embeddingDims
    ) {
    }
}
