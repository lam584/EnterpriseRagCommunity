package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class VectorIndexMetadataSupport {

    private VectorIndexMetadataSupport() {
    }

    public static void touchMetadata(
            Long vectorIndexId,
            VectorIndicesRepository vectorIndicesRepository,
            Consumer<Map<String, Object>> mutator
    ) {
        if (vectorIndexId == null || vectorIndicesRepository == null) return;
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId).orElse(null);
        if (vi == null) return;
        Map<String, Object> meta0 = vi.getMetadata();
        Map<String, Object> meta = meta0 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(meta0);
        if (mutator != null) mutator.accept(meta);
        vi.setMetadata(meta);
        vectorIndicesRepository.save(vi);
    }

    public static Map<String, Object> prepareBuildMetadata(
            VectorIndicesEntity vectorIndex,
            VectorIndicesRepository vectorIndicesRepository,
            Integer dimsToUse,
            long failedCount,
            String indexName,
            String sourceType
    ) {
        applyBuildCompletionState(vectorIndex, vectorIndicesRepository, dimsToUse, failedCount);
        Map<String, Object> meta = copyMetadata(vectorIndex);
        meta.remove("embeddingModel");
        meta.put("esIndex", indexName);
        meta.put("sourceType", sourceType);
        return meta;
    }

    public static void applyBuildCompletionState(
            VectorIndicesEntity vectorIndex,
            VectorIndicesRepository vectorIndicesRepository,
            Integer dimsToUse,
            long failedCount
    ) {
        if (vectorIndex == null) return;
        if (dimsToUse != null && dimsToUse > 0) {
            if (vectorIndex.getDim() == null || vectorIndex.getDim() <= 0) {
                vectorIndex.setDim(dimsToUse);
            } else if (!vectorIndex.getDim().equals(dimsToUse)) {
                vectorIndex.setStatus(VectorIndexStatus.ERROR);
                if (vectorIndicesRepository != null) {
                    vectorIndicesRepository.save(vectorIndex);
                }
                throw new IllegalStateException("vector index dim mismatch: stored=" + vectorIndex.getDim() + ", embedding=" + dimsToUse);
            }
        }
        vectorIndex.setMetric(vectorIndex.getMetric() == null || vectorIndex.getMetric().isBlank() ? "cosine" : vectorIndex.getMetric());
        vectorIndex.setStatus(failedCount > 0 ? VectorIndexStatus.ERROR : VectorIndexStatus.READY);
    }

    public static Map<String, Object> copyMetadata(VectorIndicesEntity vectorIndex) {
        Map<String, Object> meta0 = vectorIndex == null ? null : vectorIndex.getMetadata();
        return meta0 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(meta0);
    }

    public static void putBuildEmbeddingMetadata(
            Map<String, Object> metadata,
            Integer chunkMaxChars,
            Integer chunkOverlapChars,
            Integer dimsToUse,
            String modelToUse,
            String providerToUse,
            Boolean cleared,
            String clearError
    ) {
        if (metadata == null) return;
        metadata.put("lastBuildChunkMaxChars", chunkMaxChars);
        metadata.put("lastBuildChunkOverlapChars", chunkOverlapChars);
        if (dimsToUse != null && dimsToUse > 0) metadata.put("lastBuildEmbeddingDims", dimsToUse);
        if (modelToUse != null && !modelToUse.isBlank()) metadata.put("lastBuildEmbeddingModel", modelToUse);
        if (providerToUse != null && !providerToUse.isBlank()) metadata.put("lastBuildEmbeddingProviderId", providerToUse);
        if (cleared != null) metadata.put("lastBuildCleared", cleared);
        if (clearError != null) metadata.put("lastBuildClearError", clearError);
    }
}
