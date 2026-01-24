package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class RagPostsTestQueryResponse {
    private String indexName;
    private Integer topK;
    private Long boardId;
    private Integer embeddingDims;
    private String embeddingModel;
    private Integer numCandidates;
    private Long tookMs;
    private List<Hit> hits;

    @Data
    public static class Hit {
        private String docId;
        private Double score;
        private Long postId;
        private Integer chunkIndex;
        private Long authorId;
        private Long boardId;
        private String title;
        private String contentTextPreview;
    }
}
