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
    private List<Message> promptMessages;
    private List<String> images;
    private String inputMode;
    private Stages stages;

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    @Data
    public static class Stages {
        private Stage text;
        private Stage image;
        private Stage cross;
    }

    @Data
    public static class Stage {
        private String decision;
        private Double score;
        private List<String> reasons;
        private List<String> riskTags;
        private String rawModelOutput;
        private String model;
        private Long latencyMs;
        private Usage usage;
        private String description;
        private String inputMode;
    }
}

