package com.example.EnterpriseRagCommunity.service.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagFileAssetIndexAsyncService {

    private final RagFileAssetIndexBuildService ragFileAssetIndexBuildService;

    @Async("ragIndexExecutor")
    public void syncSingleFileAssetAsync(Long vectorIndexId, Long fileAssetId) {
        if (vectorIndexId == null || fileAssetId == null) return;
        try {
            ragFileAssetIndexBuildService.syncSingleFileAsset(vectorIndexId, fileAssetId);
        } catch (Exception ignore) {
        }
    }
}
