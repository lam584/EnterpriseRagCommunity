package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class ContextClipTestResponse {
    private String queryText;
    private Long boardId;

    private ContextClipConfigDTO config;

    private Integer budgetTokens;
    private Integer usedTokens;
    private Integer itemsSelected;
    private Integer itemsDropped;

    private String contextPrompt;

    private List<Item> selected;
    private List<Item> dropped;

    @Data
    public static class Item {
        private Integer rank;
        private Long postId;
        private Integer chunkIndex;
        private Double score;
        private String title;
        private Integer tokens;
        private String reason;
    }
}

