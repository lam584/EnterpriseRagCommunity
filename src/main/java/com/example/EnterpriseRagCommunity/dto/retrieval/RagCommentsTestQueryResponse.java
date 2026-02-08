package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class RagCommentsTestQueryResponse {
    private String indexName;
    private Integer topK;
    private Integer embeddingDims;
    private String embeddingModel;
    private Integer numCandidates;
    private Long tookMs;
    private List<Hit> hits;

    @Data
    public static class Hit {
        private String docId;
        private Double score;
        private Long commentId;
        private Long postId;
        private Long parentId;
        private Long authorId;
        private Integer commentFloor;
        private Integer commentLevel;
        private String contentExcerpt;
        private String contentTextPreview;
    }
}

