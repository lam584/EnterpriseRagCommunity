package com.example.EnterpriseRagCommunity.service.retrieval.jobs;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.RagAutoSyncConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class RagFileAssetAutoSyncJob {

    private final RagAutoSyncConfigService configService;
    private final VectorIndicesRepository vectorIndicesRepository;
    private final RagFileAssetIndexBuildService buildService;

    private final AtomicLong lastRunAtMs = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${app.retrieval.rag.autoSync.poll-ms:5000}")
    public void tick() {
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
            if (sourceType != null && !"FILE_ASSET".equalsIgnoreCase(sourceType.trim())) continue;

            Integer fileBatchSize = toInt(meta == null ? null : meta.get("lastBuildFileBatchSize"));
            Integer chunkMaxChars = toInt(meta == null ? null : meta.get("lastBuildChunkMaxChars"));
            Integer chunkOverlapChars = toInt(meta == null ? null : meta.get("lastBuildChunkOverlapChars"));
            Integer embeddingDims = toInt(meta == null ? null : meta.get("lastBuildEmbeddingDims"));

            buildService.syncFilesIncremental(vi.getId(), fileBatchSize, chunkMaxChars, chunkOverlapChars, null, null, embeddingDims);
        }
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
