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
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.RagAutoSyncConfigService;

import lombok.RequiredArgsConstructor;

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
        if (!RagAutoSyncSupport.shouldRun(cfg, lastRunAtMs)) return;

        List<VectorIndicesEntity> indices = vectorIndicesRepository.findByStatus(VectorIndexStatus.READY);
        for (VectorIndicesEntity vi : indices) {
            if (!com.example.EnterpriseRagCommunity.service.retrieval.RagValueSupport.matchesSourceType(vi, "FILE_ASSET")) continue;
            Map<String, Object> meta = vi.getMetadata();
            RagAutoSyncSupport.BuildChunkParams chunkParams = RagAutoSyncSupport.buildChunkParams(meta);

            Integer fileBatchSize = RagAutoSyncSupport.toInt(meta == null ? null : meta.get("lastBuildFileBatchSize"));

            buildService.syncFilesIncremental(
                    vi.getId(),
                    fileBatchSize,
                    chunkParams.chunkMaxChars(),
                    chunkParams.chunkOverlapChars(),
                    null,
                    null,
                    chunkParams.embeddingDims()
            );
        }
    }
}
