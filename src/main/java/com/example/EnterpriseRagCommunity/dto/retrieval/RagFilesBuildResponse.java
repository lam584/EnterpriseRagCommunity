package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class RagFilesBuildResponse {
    @Data
    public static class FailedDoc {
        private String docId;
        private String error;
    }

    private long totalFiles;
    private long totalChunks;
    private long successChunks;
    private long failedChunks;
    private List<String> failedDocIds;
    private List<FailedDoc> failedDocs;
    private Long fromFileAssetId;
    private Long lastFileAssetId;
    private Integer fileBatchSize;
    private Integer chunkMaxChars;
    private Integer chunkOverlapChars;
    private Integer embeddingDims;
    private String embeddingModel;
    private String embeddingProviderId;
    private Boolean cleared;
    private String clearError;
    private Long tookMs;
}

