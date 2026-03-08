package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.util.List;

@Data
public class LlmModerationTestResponse {
    private String decisionSuggestion; // ALLOW | REJECT | ESCALATE
    private String decision; // APPROVE | REJECT | HUMAN
    private Double riskScore;
    private Double score;
    private List<String> reasons;
    private List<String> labels;
    private List<String> riskTags;
    private LabelTaxonomy labelTaxonomy;
    private String severity;
    private Double uncertainty;
    private List<String> evidence;

    private String rawModelOutput;
    private String model;
    private Long latencyMs;

    private Usage usage;
    private List<Message> promptMessages;
    private List<String> images;
    private List<ImageResult> imageResults;
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
        private Stage judge;
        private Stage upgrade;
    }

    @Data
    public static class LabelTaxonomy {
        private String taxonomyId;
        private List<String> allowedLabels;
        private List<LabelItem> labelMap;
    }

    @Data
    public static class LabelItem {
        private String slug;
        private String name;
    }

    @Data
    public static class Stage {
        private String decisionSuggestion;
        private String decision;
        private Double riskScore;
        private Double score;
        private List<String> reasons;
        private List<String> labels;
        private List<String> riskTags;
        private String severity;
        private Double uncertainty;
        private List<String> evidence;
        private String rawModelOutput;
        private String model;
        private Long latencyMs;
        private Usage usage;
        private String description;
        private String inputMode;
    }

    @Data
    public static class ImageResult {
        private String imageId;
        private String url;
        private Stage result;
    }
}

