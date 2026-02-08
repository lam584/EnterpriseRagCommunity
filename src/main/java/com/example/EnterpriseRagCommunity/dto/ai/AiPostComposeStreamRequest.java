package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiPostComposeStreamRequest {
    @NotNull
    private Long snapshotId;

    @Size(max = 8000)
    private String instruction;

    @Size(max = 256)
    private String currentTitle;

    @Size(max = 100000)
    private String currentContent;

    @Size(max = 20)
    private List<ChatHistoryMessage> chatHistory;

    @Size(max = 128)
    private String providerId;

    @Size(max = 128)
    private String model;

    private Double temperature;

    private Double topP;

    @NotNull
    private Boolean deepThink = false;

    private List<ImageInput> images;

    @Data
    public static class ChatHistoryMessage {
        @NotNull
        @Size(max = 16)
        private String role;

        @Size(max = 2000)
        private String content;
    }

    @Data
    public static class ImageInput {
        private Long fileAssetId;

        @Size(max = 2048)
        private String url;

        @Size(max = 128)
        private String mimeType;
    }
}
