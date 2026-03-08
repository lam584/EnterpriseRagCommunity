package com.example.EnterpriseRagCommunity.service.retrieval.jobs;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.RagAutoSyncConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class RagPostAutoSyncJob {

    private final RagAutoSyncConfigService configService;
    private final VectorIndicesRepository vectorIndicesRepository;
    private final RagPostIndexBuildService buildService;
    private final SystemConfigurationService systemConfigurationService;

    private final AtomicLong lastRunAtMs = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${app.retrieval.rag.autoSync.poll-ms:5000}")
    public void tick() {
        // Check for ES API Key
        String apiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return; // Skip if ES is not configured
        }

        RagAutoSyncConfigDTO cfg = configService.getConfig();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return;

        long intervalMs = (cfg.getIntervalSeconds() == null ? 30 : cfg.getIntervalSeconds()) * 1000L;
        intervalMs = Math.max(5000L, Math.min(3_600_000L, intervalMs));

        long now = System.currentTimeMillis();
        long prev = lastRunAtMs.get();
        if (now - prev < intervalMs) return;
        if (!lastRunAtMs.compareAndSet(prev, now)) return;

        List<VectorIndicesEntity> indices = vectorIndicesRepository.findByStatus(VectorIndexStatus.READY);
        for (VectorIndicesEntity vi : indices) {
            if (vi == null || vi.getId() == null) continue;
            Map<String, Object> meta = vi.getMetadata();
            String sourceType = meta == null ? null : (meta.get("sourceType") == null ? null : String.valueOf(meta.get("sourceType")));
            if (sourceType != null && !"POST".equalsIgnoreCase(sourceType.trim())) continue;

            Long boardId = toLong(meta == null ? null : meta.get("lastSyncBoardId"));
            if (boardId == null) boardId = toLong(meta == null ? null : meta.get("lastBuildBoardId"));
            Integer postBatchSize = toInt(meta == null ? null : meta.get("lastBuildPostBatchSize"));
            Integer chunkMaxChars = toInt(meta == null ? null : meta.get("lastBuildChunkMaxChars"));
            Integer chunkOverlapChars = toInt(meta == null ? null : meta.get("lastBuildChunkOverlapChars"));
            Integer embeddingDims = toInt(meta == null ? null : meta.get("lastBuildEmbeddingDims"));

            buildService.syncPostsIncremental(vi.getId(), boardId, postBatchSize, chunkMaxChars, chunkOverlapChars, null, null, embeddingDims);
        }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String toStr(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }
}
