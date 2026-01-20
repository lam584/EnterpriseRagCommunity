package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

@Data
public class LlmModerationTestRequest {
    private Long queueId;
    private String text;
    private LlmModerationConfigOverrideDTO configOverride;

    @Data
    public static class LlmModerationConfigOverrideDTO {
        private String promptTemplate;
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Double threshold;
        private Boolean autoRun;

        private Integer maxConcurrent;
        private Integer minDelayMs;
        private Double qps;
    }
}
