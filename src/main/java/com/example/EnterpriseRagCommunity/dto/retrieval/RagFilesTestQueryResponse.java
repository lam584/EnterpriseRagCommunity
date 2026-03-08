package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class RagFilesTestQueryResponse {
    private String indexName;
    private Integer topK;
    private Long fileAssetId;
    private Long postId;
    private Integer embeddingDims;
    private String embeddingModel;
    private String embeddingProviderId;
    private Integer numCandidates;
    private Long tookMs;
    private List<Hit> hits;

    @Data
    public static class Hit {
        private String docId;
        private Double score;
        private Long fileAssetId;
        private Integer chunkIndex;
        private Long ownerUserId;
        private String fileName;
        private String mimeType;
        private List<Long> postIds;
        private String contentTextPreview;
    }
}

