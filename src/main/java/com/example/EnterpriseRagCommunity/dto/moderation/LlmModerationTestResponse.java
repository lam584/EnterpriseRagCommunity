package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.util.List;

@Data
public class LlmModerationTestResponse {
    private String decision; // APPROVE | REJECT | HUMAN
    private Double score;
    private List<String> reasons;
    private List<String> riskTags;

    private String rawModelOutput;
    private String model;
    private Long latencyMs;

    private Usage usage;

    @Data
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}

