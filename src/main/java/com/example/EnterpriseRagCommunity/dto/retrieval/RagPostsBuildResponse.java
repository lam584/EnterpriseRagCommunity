package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class RagPostsBuildResponse {
    @Data
    public static class FailedDoc {
        private String docId;
        private String error;
    }

    private long totalPosts;
    private long totalChunks;
    private long successChunks;
    private long failedChunks;
    private List<String> failedDocIds;
    private List<FailedDoc> failedDocs;
    private Long fromPostId;
    private Long lastPostId;
    private Long boardId;
    private Integer postBatchSize;
    private Integer chunkMaxChars;
    private Integer chunkOverlapChars;
    private Integer embeddingDims;
    private String embeddingModel;
    private Boolean cleared;
    private String clearError;
    private Long tookMs;
}
