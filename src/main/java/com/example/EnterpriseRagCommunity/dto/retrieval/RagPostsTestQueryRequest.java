package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class RagPostsTestQueryRequest {
    private String queryText;
    private Integer topK;
    private Long boardId;
    private Integer numCandidates;
    private String embeddingModel;
    private String embeddingProviderId;
}
