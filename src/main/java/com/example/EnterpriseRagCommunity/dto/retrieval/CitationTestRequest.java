package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class CitationTestRequest {
    private Boolean useSavedConfig;
    private CitationConfigDTO config;
    private List<CitationTestItem> items;

    @Data
    public static class CitationTestItem {
        private Long postId;
        private Integer chunkIndex;
        private Double score;
        private String title;
    }
}

