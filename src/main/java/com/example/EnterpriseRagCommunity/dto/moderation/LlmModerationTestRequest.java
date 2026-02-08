package com.example.EnterpriseRagCommunity.dto.moderation;

import java.util.List;

import lombok.Data;

@Data
public class LlmModerationTestRequest {
    private Long queueId;
    private String text;
    private List<ImageInput> images;
    private LlmModerationConfigOverrideDTO configOverride;

    @Data
    public static class ImageInput {
        private Long fileAssetId;
        private String url;
        private String mimeType;
    }

    @Data
    public static class LlmModerationConfigOverrideDTO {
        private String promptTemplate;
        private String visionPromptTemplate;
        private String model;
        private String providerId;
        private String visionModel;
        private String visionProviderId;
        private Double temperature;
        private Double visionTemperature;
        private Integer maxTokens;
        private Integer visionMaxTokens;
        private Double threshold;
        private Boolean autoRun;

        private Integer maxConcurrent;
        private Integer minDelayMs;
        private Double qps;
    }
}
