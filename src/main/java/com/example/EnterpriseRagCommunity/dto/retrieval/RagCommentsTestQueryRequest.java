package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class RagCommentsTestQueryRequest {
    private String queryText;
    private Integer topK;
    private Integer numCandidates;
    private String embeddingModel;
}

