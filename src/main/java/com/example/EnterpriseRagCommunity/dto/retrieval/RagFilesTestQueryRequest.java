package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class RagFilesTestQueryRequest {
    private String queryText;
    private Integer topK;
    private Long fileAssetId;
    private Long postId;
    private Integer numCandidates;
    private String embeddingModel;
    private String embeddingProviderId;
}

