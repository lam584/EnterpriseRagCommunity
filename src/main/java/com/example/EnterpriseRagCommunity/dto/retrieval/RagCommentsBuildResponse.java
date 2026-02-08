package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class RagCommentsBuildResponse {
    @Data
    public static class FailedDoc {
        private String docId;
        private String error;
    }

    private long totalComments;
    private long totalChunks;
    private long successChunks;
    private long failedChunks;
    private List<String> failedDocIds;
    private List<FailedDoc> failedDocs;
    private Long fromCommentId;
    private Long lastCommentId;
    private Integer commentBatchSize;
    private Integer chunkMaxChars;
    private Integer chunkOverlapChars;
    private Integer embeddingDims;
    private String embeddingModel;
    private Boolean cleared;
    private String clearError;
    private Long tookMs;
}

