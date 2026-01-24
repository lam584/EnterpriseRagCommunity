package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class CitationTestResponse {
    private CitationConfigDTO config;
    private String instructionPreview;
    private String sourcesPreview;
    private List<Source> sources;

    @Data
    public static class Source {
        private Integer index;
        private Long postId;
        private Integer chunkIndex;
        private Double score;
        private String title;
        private String url;
    }
}

