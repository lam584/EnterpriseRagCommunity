package com.example.EnterpriseRagCommunity.dto.moderation;

import java.util.List;

import lombok.Data;

@Data
public class LlmModerationTestRequest {
    private Long queueId;
    private String reviewStage; // default | reported | appeal
    private String text;
    private List<ImageInput> images;
    private LlmModerationConfigOverrideDTO configOverride;
    private Boolean useQueue;

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
        private String judgePromptTemplate;
        private String systemPrompt;
        private String visionSystemPrompt;
        private Boolean autoRun;
    }
}
