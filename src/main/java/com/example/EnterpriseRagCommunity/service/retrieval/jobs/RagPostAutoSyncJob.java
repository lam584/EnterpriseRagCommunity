package com.example.EnterpriseRagCommunity.service.retrieval.jobs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.RagAutoSyncConfigService;

import lombok.RequiredArgsConstructor;

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
        if (!RagAutoSyncSupport.shouldRun(cfg, lastRunAtMs)) return;

        List<VectorIndicesEntity> indices = vectorIndicesRepository.findByStatus(VectorIndexStatus.READY);
        for (VectorIndicesEntity vi : indices) {
            if (!com.example.EnterpriseRagCommunity.service.retrieval.RagValueSupport.matchesSourceType(vi, "POST")) continue;
            Map<String, Object> meta = vi.getMetadata();
            RagAutoSyncSupport.BuildChunkParams chunkParams = RagAutoSyncSupport.buildChunkParams(meta);

            Long boardId = RagAutoSyncSupport.toLong(meta == null ? null : meta.get("lastSyncBoardId"));
            if (boardId == null) boardId = RagAutoSyncSupport.toLong(meta == null ? null : meta.get("lastBuildBoardId"));
            Integer postBatchSize = RagAutoSyncSupport.toInt(meta == null ? null : meta.get("lastBuildPostBatchSize"));

            buildService.syncPostsIncremental(
                    vi.getId(),
                    boardId,
                    postBatchSize,
                    chunkParams.chunkMaxChars(),
                    chunkParams.chunkOverlapChars(),
                    null,
                    null,
                    chunkParams.embeddingDims()
            );
        }
    }
}
